package com.namely.chiefofstate

import akka.actor.ActorSystem
import com.namely.protobuf.chief_of_state.persistence.State
import com.typesafe.config.Config
import lagompb.{LagompbAggregate, LagompbCommandHandler, LagompbEventHandler}
import scalapb.GeneratedMessageCompanion

/**
 * ChiefOfStateAggregate
 *
 * @param actorSystem    the actor system
 * @param config         config object reading the application configuration file
 * @param commandHandler the commands handler
 * @param eventHandler   the events handler
 */
class ChiefOfStateAggregate(
    actorSystem: ActorSystem,
    config: Config,
    commandHandler: LagompbCommandHandler[State],
    eventHandler: LagompbEventHandler[State]
) extends LagompbAggregate[State](actorSystem, commandHandler, eventHandler) {
  // $COVERAGE-OFF$
  override def aggregateName: String = "chiefOfState"

  override def stateCompanion: GeneratedMessageCompanion[State] = State

  // $COVERAGE-ON$
}
