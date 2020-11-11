package com.namely.chiefofstate

import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl._
import com.google.protobuf.any
import com.google.protobuf.empty.Empty
import com.namely.chiefofstate.config.{CosConfig, EventsConfig, SnapshotConfig}
import com.namely.chiefofstate.Util.{makeFailedStatusPf, toRpcStatus, Instants}
import com.namely.chiefofstate.interceptors.OpentracingHelpers
import com.namely.chiefofstate.WriteHandlerHelpers.{NewState, NoOp}
import com.namely.protobuf.chiefofstate.v1.common.MetaData
import com.namely.protobuf.chiefofstate.v1.internal.{CommandReply, GetStateCommand, RemoteCommand, SendCommand}
import com.namely.protobuf.chiefofstate.v1.persistence.{EventWrapper, StateWrapper}
import io.grpc.{Status, StatusException}
import org.slf4j.{Logger, LoggerFactory}
import io.opentracing.util.GlobalTracer
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}
import io.opentracing.Span
import io.opentracing.tag.Tags
import java.time.Instant

/**
 *  This is an event sourced actor.
 */
object AggregateRoot {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * thee aggregate root type key
   */
  val TypeKey: EntityTypeKey[AggregateCommand] = EntityTypeKey[AggregateCommand]("COS")

  /**
   * creates a new instance of the aggregate root
   *
   * @param persistenceId the internal persistence ID used by akka to locate the aggregate based upon the given entity ID.
   * @param shardIndex the shard index of the aggregate
   * @param cosConfig the main config
   * @param commandHandler the remote command handler
   * @param eventHandler the remote events handler handler
   * @return an akka behaviour
   */
  def apply(
    persistenceId: PersistenceId,
    shardIndex: Int,
    cosConfig: CosConfig,
    commandHandler: RemoteCommandHandler,
    eventHandler: RemoteEventHandler,
    eventsAndStateProtoValidation: EventsAndStateProtosValidation
  ): Behavior[AggregateCommand] = {
    Behaviors.setup { context =>
      {
        EventSourcedBehavior
          .withEnforcedReplies[AggregateCommand, EventWrapper, StateWrapper](
            persistenceId,
            emptyState = initialState(persistenceId),
            (state, command) =>
              handleCommand(context, state, command, commandHandler, eventHandler, eventsAndStateProtoValidation),
            (state, event) => handleEvent(state, event)
          )
          .withTagger(_ => Set(tags(cosConfig.eventsConfig)(shardIndex)))
          .withRetention(setSnapshotRetentionCriteria(cosConfig.snapshotConfig))
          .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
      }
    }
  }

  /**
   * handles the received command by the aggregate root
   *
   * @param context the actor system context
   * @param aggregateState the prior state of the aggregate before the command being handled is received
   * @param aggregateCommand the command to handle
   * @param commandHandler the remote commands handleer
   * @param eventHandler the remote events handler
   * @return a side effect
   */
  private[chiefofstate] def handleCommand(
    context: ActorContext[AggregateCommand],
    aggregateState: StateWrapper,
    aggregateCommand: AggregateCommand,
    commandHandler: RemoteCommandHandler,
    eventHandler: RemoteEventHandler,
    eventsAndStateProtoValidation: EventsAndStateProtosValidation
  ): ReplyEffect[EventWrapper, StateWrapper] = {

    log.debug("begin handle command")

    val headers = aggregateCommand.command.tracingHeaders

    val tracer = GlobalTracer.get()

    val span: Span = OpentracingHelpers
      .getChildSpanBuilder(tracer, headers, "AggregateRoot.handleCommand")
      .withTag(Tags.COMPONENT.getKey(), this.getClass().getName)
      .start()

    tracer.activateSpan(span)

    val output: ReplyEffect[EventWrapper, StateWrapper] = aggregateCommand.command.`type` match {
      case SendCommand.Type.Empty =>
        Effect
          .reply(aggregateCommand.replyTo)(
            CommandReply()
              .withError(toRpcStatus(Status.INTERNAL.withDescription("something really bad happens...")))
          )

      case SendCommand.Type.RemoteCommand(remoteCommand: RemoteCommand) =>
        handleRemoteCommand(context,
                            aggregateState,
                            remoteCommand,
                            aggregateCommand.replyTo,
                            commandHandler,
                            eventHandler,
                            eventsAndStateProtoValidation,
                            aggregateCommand.data
        )

      case SendCommand.Type.GetStateCommand(getStateCommand) =>
        handleGetStateCommand(getStateCommand, aggregateState, aggregateCommand.replyTo)
    }

    span.finish()

    output
  }

  /**
   * handles GetStateCommand
   *
   * @param cmd a GetStateCommand
   * @param state an aggregate StateWrapper
   * @param replyTo address to reply to
   * @return a reply effect returning the state or an error
   */
  def handleGetStateCommand(cmd: GetStateCommand,
                            state: StateWrapper,
                            replyTo: ActorRef[CommandReply]
  ): ReplyEffect[EventWrapper, StateWrapper] = {
    if (state.meta.map(_.revisionNumber).getOrElse(0) > 0) {
      log.debug(s"found state for entity ${cmd.entityId}")
      Effect.reply(replyTo)(CommandReply().withState(state))
    } else {
      Effect.reply(replyTo)(
        CommandReply()
          .withError(toRpcStatus(Status.NOT_FOUND))
      )
    }
  }

  /**
   * hanlder for remote commands
   *
   * @param context actor context
   * @param priorState the prior state of the entity
   * @param command the command to handle
   * @param replyTo the actor ref to reply to
   * @param commandHandler a command handler
   * @param eventHandler an event handler
   * @param eventsAndStateProtoValidation a proto validator
   * @param data COS plugin data
   * @return a reply effect
   */
  def handleRemoteCommand(context: ActorContext[AggregateCommand],
                          priorState: StateWrapper,
                          command: RemoteCommand,
                          replyTo: ActorRef[CommandReply],
                          commandHandler: RemoteCommandHandler,
                          eventHandler: RemoteEventHandler,
                          eventsAndStateProtoValidation: EventsAndStateProtosValidation,
                          data: Map[String, com.google.protobuf.any.Any]
  ): ReplyEffect[EventWrapper, StateWrapper] = {

    val handlerOutput: Try[WriteHandlerHelpers.WriteTransitions] = commandHandler
      .handleCommand(command, priorState)
      .map(_.event match {
        case Some(newEvent) =>
          eventsAndStateProtoValidation.requireValidEvent(newEvent)
          WriteHandlerHelpers.NewEvent(newEvent)

        case None =>
          WriteHandlerHelpers.NoOp
      })
      .flatMap({
        case WriteHandlerHelpers.NewEvent(newEvent) =>
          eventHandler
            .handleEvent(newEvent, priorState)
            .map(response => {
              require(response.resultingState.isDefined, "event handler replied with empty state")
              eventsAndStateProtoValidation.requireValidState(response.getResultingState)
              WriteHandlerHelpers.NewState(newEvent, response.getResultingState)
            })

        case x =>
          Success(x)
      })
      .recoverWith(makeFailedStatusPf)

    handlerOutput match {
      case Success(NoOp) =>
        Effect.reply(replyTo)(CommandReply().withState(priorState))

      case Success(NewState(event, newState)) =>
        persistEventAndReply(event, newState, priorState.getMeta, data, replyTo)

      case Failure(e: StatusException) =>
        Effect.reply(replyTo)(
          CommandReply().withError(toRpcStatus(e.getStatus))
        )

      case x =>
        // this should never happen, but here for code completeness
        val errMsg: String = s"write handler failure, ${x.getClass}"
        Effect.reply(replyTo)(
          CommandReply().withError(toRpcStatus(Status.INTERNAL.withDescription(errMsg)))
        )
    }
  }

  /**
   * handles the aggregate event persisted by applying the prior state to the
   * event to return a new state
   *
   * @param state the prior state to the event being handled
   * @param event the event to handle
   * @return the resulting state
   */
  private[chiefofstate] def handleEvent(
    state: StateWrapper,
    event: EventWrapper
  ): StateWrapper = {
    state.update(_.meta := event.getMeta, _.state := event.getResultingState)
  }

  /**
   * sets the snapshot retention criteria
   *
   * @param snapshotConfig the snapshot configt
   * @return the snapshot retention criteria
   */
  private[chiefofstate] def setSnapshotRetentionCriteria(snapshotConfig: SnapshotConfig): RetentionCriteria = {
    if (snapshotConfig.disableSnapshot) RetentionCriteria.disabled
    else {
      // journal/snapshot retention criteria
      val rc: SnapshotCountRetentionCriteria = RetentionCriteria
        .snapshotEvery(
          numberOfEvents = snapshotConfig.retentionFrequency, // snapshotFrequency
          keepNSnapshots = snapshotConfig.retentionNr //snapshotRetention
        )
      // journal/snapshot retention criteria
      if (snapshotConfig.deleteEventsOnSnapshot) rc.withDeleteEventsOnSnapshot
      rc
    }
  }

  /**
   * returns the list of possible event tags baseed upon the number of shards defined
   * in the configuration file
   *
   * @param eventsConfig the events config
   * @return the list of tags
   */
  def tags(eventsConfig: EventsConfig): Vector[String] = {
    (for (shardNo <- 0 until eventsConfig.numShards)
      yield s"${eventsConfig.eventTag}$shardNo").toVector
  }

  /**
   * returns the peristence actual entity id from the persistence ID
   *
   * @param persistenceId the persistence ID
   * @return the actual entity ID
   */
  private[chiefofstate] def getEntityId(persistenceId: PersistenceId): String = {
    val splitter: Char = PersistenceId.DefaultSeparator(0)
    persistenceId.id.split(splitter).lastOption.getOrElse("")
  }

  /**
   * perists an event and the resulting state and reply to the caller
   *
   * @param event the event to persist
   * @param resultingState the resulting state to persist
   * @param priorMeta the prior meta before the event to be persisted
   * @param data the additional data to persist
   * @param replyTo the caller ref receiving the reply when persistence is successful
   * @return a reply effect
   */
  private[chiefofstate] def persistEventAndReply(
    event: any.Any,
    resultingState: any.Any,
    priorMeta: MetaData,
    data: Map[String, any.Any],
    replyTo: ActorRef[CommandReply]
  ): ReplyEffect[EventWrapper, StateWrapper] = {
    val meta: MetaData = MetaData()
      .withRevisionNumber(priorMeta.revisionNumber + 1)
      .withRevisionDate(Instant.now().toTimestamp)
      .withData(data)
      .withEntityId(priorMeta.entityId)

    Effect
      .persist(
        EventWrapper()
          .withEvent(event)
          .withResultingState(resultingState)
          .withMeta(meta)
      )
      .thenReply(replyTo)((updatedState: StateWrapper) => CommandReply().withState(updatedState))
  }

  /**
   * creates the initial state of the aggregate
   *
   * @param persistenceId the persistence ID
   * @return the initial state
   */
  private[chiefofstate] def initialState(persistenceId: PersistenceId): StateWrapper = {
    StateWrapper.defaultInstance
      .withMeta(
        MetaData.defaultInstance.withEntityId(getEntityId(persistenceId))
      )
      .withState(any.Any.pack(Empty.defaultInstance))
  }
}

object WriteHandlerHelpers {
  sealed trait WriteTransitions
  case object NoOp extends WriteTransitions
  case class NewEvent(event: com.google.protobuf.any.Any) extends WriteTransitions
  case class NewState(event: com.google.protobuf.any.Any, state: com.google.protobuf.any.Any) extends WriteTransitions
}
