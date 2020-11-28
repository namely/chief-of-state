package com.namely.chiefofstate

import java.time.Instant

import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl._
import com.google.protobuf.any
import com.google.protobuf.empty.Empty
import com.namely.chiefofstate.config.{CosConfig, EventsConfig, SnapshotConfig}
import com.namely.chiefofstate.Util.{toRpcStatus, Instants}
import com.namely.chiefofstate.interceptors.OpentracingHelpers
import com.namely.chiefofstate.WriteHandlerHelper.NoOpException
import com.namely.protobuf.chiefofstate.v1.common.MetaData
import com.namely.protobuf.chiefofstate.v1.internal.{CommandReply, GetStateCommand, RemoteCommand, SendCommand}
import com.namely.protobuf.chiefofstate.v1.persistence.{EventWrapper, StateWrapper}
import com.namely.protobuf.chiefofstate.v1.writeside.{HandleCommandResponse, HandleEventResponse}
import io.grpc.Status
import io.opentracing.{Span, Tracer}
import io.opentracing.tag.Tags
import io.opentracing.util.GlobalTracer
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

/**
 *  This is an event sourced actor.
 */
object AggregateRoot {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  lazy val tracer: Tracer = GlobalTracer.get()

  /**
   * thee aggregate root type key
   */
  val TypeKey: EntityTypeKey[AggregateCommand] = EntityTypeKey[AggregateCommand]("chiefOfState")

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
    protosValidator: ProtosValidator
  ): Behavior[AggregateCommand] = {
    Behaviors.setup { context =>
      {
        EventSourcedBehavior
          .withEnforcedReplies[AggregateCommand, EventWrapper, StateWrapper](
            persistenceId,
            emptyState = initialState(persistenceId),
            (state, command) => handleCommand(context, state, command, commandHandler, eventHandler, protosValidator),
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
    eventsAndStateProtoValidation: ProtosValidator
  ): ReplyEffect[EventWrapper, StateWrapper] = {

    log.debug("begin handle command")

    val headers: Map[String, String] = aggregateCommand.command.tracingHeaders

    val tracer: Tracer = GlobalTracer.get()

    val span: Span = OpentracingHelpers
      .getChildSpanBuilder(tracer, headers, "AggregateRoot.handleCommand")
      .withTag(Tags.COMPONENT.getKey, this.getClass.getName)
      .start()

    tracer.activateSpan(span)

    val output: ReplyEffect[EventWrapper, StateWrapper] = aggregateCommand.command.`type` match {
      case SendCommand.Type.Empty =>
        val errStatus = Status.INTERNAL.withDescription("something really bad happens...")

        OpentracingHelpers.reportErrorToTracer(tracer, errStatus.asException())

        Effect
          .reply(aggregateCommand.replyTo)(
            CommandReply()
              .withError(toRpcStatus(errStatus))
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
  def handleGetStateCommand(
    cmd: GetStateCommand,
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
   * checks whether an event is present in the command handler response.
   *
   * @param handleCommandResponse the command handler response
   */
  private[chiefofstate] def requireNewEvent(handleCommandResponse: HandleCommandResponse): Unit = {
    if (handleCommandResponse.event.isEmpty) throw NoOpException()
  }

  /**
   * checks whether the resulting state is present in the event handler response.
   *
   * @param handleEventResponse the event handler response
   */
  private[chiefofstate] def requireNewState(handleEventResponse: HandleEventResponse): Unit = {
    if (handleEventResponse.resultingState.isEmpty)
      throw new IllegalArgumentException("requirement failed: event handler replied with empty state")
  }

  /**
   * returns the new event meta based upon the prior state and additional data
   *
   * @param priorState the prior state
   * @param data the additional data
   * @return the new event meta
   */
  private[chiefofstate] def newEventMeta(
    priorState: StateWrapper,
    data: Map[String, com.google.protobuf.any.Any]
  ): MetaData = {
    MetaData()
      .withRevisionNumber(priorState.getMeta.revisionNumber + 1)
      .withRevisionDate(Instant.now().toTimestamp)
      .withData(data)
      .withEntityId(priorState.getMeta.entityId)
  }

  /**
   * processes the write handler request flow
   *
   * @param priorState the prior state
   * @param command the command to process
   * @param commandHandler the command handler
   * @param eventHandler the event handler
   * @param protosValidator the protos validation
   * @param data additional data
   * @return
   */
  private def process(
    command: RemoteCommand,
    priorState: StateWrapper,
    commandHandler: RemoteCommandHandler,
    eventHandler: RemoteEventHandler,
    protosValidator: ProtosValidator,
    data: Map[String, com.google.protobuf.any.Any]
  ): Try[(any.Any, any.Any, MetaData)] =
    for {
      // send the command to the command handler
      cmdHandlerResponse <- commandHandler.handleCommand(command, priorState)

      // validate the command handler response
      _ <- Try(requireNewEvent(cmdHandlerResponse))

      // check whether a valid event is emitted or not
      _ <- Try(protosValidator.requireValidEvent(cmdHandlerResponse.getEvent))

      // construct a new event meta data
      newEventMeta <- Try(newEventMeta(priorState, data))

      // call the event handler to obtain the resulting state
      eventHandlerResponse <- eventHandler.handleEvent(cmdHandlerResponse.getEvent, priorState.getState, newEventMeta)

      // validate the event handler response
      _ <- Try(requireNewState(eventHandlerResponse))

      // check whether the resulting state is valid or not
      _ <- Try(protosValidator.requireValidState(eventHandlerResponse.getResultingState))
    } yield (cmdHandlerResponse.getEvent, eventHandlerResponse.getResultingState, newEventMeta)

  /**
   * hanlder for remote commands
   *
   * @param context actor context
   * @param priorState the prior state of the entity
   * @param command the command to handle
   * @param replyTo the actor ref to reply to
   * @param commandHandler a command handler
   * @param eventHandler an event handler
   * @param protosValidator a proto validator
   * @param data COS plugin data
   * @return a reply effect
   */
  def handleRemoteCommand(
    context: ActorContext[AggregateCommand],
    priorState: StateWrapper,
    command: RemoteCommand,
    replyTo: ActorRef[CommandReply],
    commandHandler: RemoteCommandHandler,
    eventHandler: RemoteEventHandler,
    protosValidator: ProtosValidator,
    data: Map[String, com.google.protobuf.any.Any]
  ): ReplyEffect[EventWrapper, StateWrapper] = {
    process(command, priorState, commandHandler, eventHandler, protosValidator, data) match {
      case Success((event, newState, meta)) => persistEventAndReply(event, newState, meta, replyTo)
      case Failure(exception) =>
        exception match {
          case _: NoOpException => Effect.reply(replyTo)(CommandReply().withState(priorState))
          case e                => handleException(e, replyTo)
        }
    }
  }

  /**
   * sends a CommandReply with error response
   *
   * @param exception the error object
   * @param replyTo the receiver of the message
   * @return CommandReply message
   */
  private[chiefofstate] def handleException(
    exception: Throwable,
    replyTo: ActorRef[CommandReply]
  ): ReplyEffect[EventWrapper, StateWrapper] = {
    // propagate the traces
    OpentracingHelpers
      .reportErrorToTracer(tracer, exception)

    // send the reply
    Effect.reply(replyTo)(
      CommandReply().withError(toRpcStatus(Util.makeStatusException(exception).getStatus))
    )
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
   * returns the list of possible event tags based upon the number of shards defined
   * in the configuration file
   *
   * @param eventsConfig the events config
   * @return the list of tags
   */
  def tags(eventsConfig: EventsConfig): Vector[String] = {
    (0 until eventsConfig.numShards)
      .map(shardNo => s"${eventsConfig.eventTag}$shardNo")
      .toVector
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
   * @param eventMeta the prior meta before the event to be persisted
   * @param replyTo the caller ref receiving the reply when persistence is successful
   * @return a reply effect
   */
  private[chiefofstate] def persistEventAndReply(
    event: any.Any,
    resultingState: any.Any,
    eventMeta: MetaData,
    replyTo: ActorRef[CommandReply]
  ): ReplyEffect[EventWrapper, StateWrapper] = {

    Effect
      .persist(
        EventWrapper()
          .withEvent(event)
          .withResultingState(resultingState)
          .withMeta(eventMeta)
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

object WriteHandlerHelper {
  case class NoOpException() extends RuntimeException
}
