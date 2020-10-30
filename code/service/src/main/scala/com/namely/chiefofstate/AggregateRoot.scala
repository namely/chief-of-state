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
import com.namely.cos.Util.Instants
import com.namely.protobuf.chiefofstate.v1.common.MetaData
import com.namely.protobuf.chiefofstate.v1.internal.{CommandReply, FailureResponse, HandleCommand}
import com.namely.protobuf.chiefofstate.v1.internal.SendCommand.Type
import com.namely.protobuf.chiefofstate.v1.persistence.{EventWrapper, StateWrapper}
import com.namely.protobuf.chiefofstate.v1.writeside.{HandleCommandResponse, HandleEventResponse}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

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
    eventHandler: RemoteEventHandler
  ): Behavior[AggregateCommand] = {
    Behaviors.setup { context =>
      {
        EventSourcedBehavior
          .withEnforcedReplies[AggregateCommand, EventWrapper, StateWrapper](
            persistenceId,
            emptyState = StateWrapper.defaultInstance
              .withMeta(
                MetaData.defaultInstance.withEntityId(getEntityId(persistenceId))
              )
              .withState(any.Any.pack(Empty.defaultInstance)),
            (state, command) => handleCommand(context, state, command, commandHandler, eventHandler),
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
  private[this] def handleCommand(
    context: ActorContext[AggregateCommand],
    aggregateState: StateWrapper,
    aggregateCommand: AggregateCommand,
    commandHandler: RemoteCommandHandler,
    eventHandler: RemoteEventHandler
  ): ReplyEffect[EventWrapper, StateWrapper] = {

    aggregateCommand.command.`type` match {
      case Type.Empty =>
        Effect
          .reply(aggregateCommand.replyTo)(
            CommandReply().withFailure(
              FailureResponse().withCritical("something really bad happens...")
            )
          )

      case Type.HandleCommand(handleCommand: HandleCommand) =>
        // make a call to the command handler.
        val commandHandlerResponseAttempt: Try[HandleCommandResponse] =
          commandHandler.handleCommand(handleCommand.getCommand, aggregateState)

        commandHandlerResponseAttempt match {

          case Failure(exception) =>
            Effect.reply(aggregateCommand.replyTo)(
              CommandReply().withFailure(
                FailureResponse().withCritical(s"[ChiefOfState] command handler failure: ${exception.getMessage}")
              )
            )

          case Success(handleCommandResponse: HandleCommandResponse) =>
            handleCommandResponse.event match {
              case Some(actualEvent) =>
                log.debug(
                  s"[ChiefOfState] command handler return successfully. The event ${actualEvent.typeUrl} will be persisted..."
                )

                val eventHandlerResponseAttempt: Try[HandleEventResponse] =
                  eventHandler.handleEvent(actualEvent, aggregateState)

                eventHandlerResponseAttempt match {
                  case Failure(exception) =>
                    Effect.reply(aggregateCommand.replyTo)(
                      CommandReply().withFailure(
                        FailureResponse().withCritical(s"[ChiefOfState] event handler failure: ${exception.getMessage}")
                      )
                    )

                  case Success(handleEventResponse: HandleEventResponse) =>
                    // let us persist the event now
                    persistEventAndReply(
                      handleCommandResponse.getEvent,
                      handleEventResponse.getResultingState,
                      aggregateState,
                      aggregateCommand.data,
                      aggregateCommand.replyTo
                    )
                }

              case None =>
                log.debug(
                  s"[ChiefOfState] command handler return successfully. No event will be persisted. Returning the current state..."
                )
                Effect
                  .reply(aggregateCommand.replyTo)(
                    CommandReply()
                      .withState(aggregateState)
                  )
            }
        }
      case Type.GetStateCommand(_) =>
        Effect
          .reply(aggregateCommand.replyTo)(
            CommandReply()
              .withState(aggregateState)
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
  private[this] def handleEvent(
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
  private[this] def setSnapshotRetentionCriteria(snapshotConfig: SnapshotConfig): RetentionCriteria = {
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
  private[this] def getEntityId(persistenceId: PersistenceId): String = {
    val splitter: Char = PersistenceId.DefaultSeparator(0)
    persistenceId.id.split(splitter).lastOption.getOrElse("")
  }

  /**
   * perists an event and the resulting state and reply to the caller
   *
   * @param event the event to persist
   * @param resultingState the resulting state to persist
   * @param priorState the prior state before the event to be persisted
   * @param data the additional data to persist
   * @param replyTo the caller ref receiving the reply when persistence is successful
   * @return a reply effect
   */
  private[this] def persistEventAndReply(
    event: any.Any,
    resultingState: any.Any,
    priorState: StateWrapper,
    data: Map[String, any.Any],
    replyTo: ActorRef[CommandReply]
  ): ReplyEffect[EventWrapper, StateWrapper] = {
    val meta: MetaData = MetaData()
      .withRevisionNumber(priorState.getMeta.revisionNumber + 1)
      .withRevisionDate(Instant.now().toTimestamp)
      .withData(data)
      .withEntityId(priorState.getMeta.entityId)

    Effect
      .persist(
        EventWrapper()
          .withEvent(event)
          .withResultingState(resultingState)
          .withMeta(meta)
      )
      .thenReply(replyTo)((updatedState: StateWrapper) => CommandReply().withState(updatedState))
  }
}
