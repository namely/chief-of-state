package com.namely.chiefofstate

import akka.actor.ActorSystem
import com.namely.protobuf.chief_of_state.persistence.State
import com.typesafe.config.Config
import io.superflat.lagompb.{AggregateRoot, CommandHandler, EventHandler}
import io.superflat.lagompb.encryption.ProtoEncryption
import scalapb.GeneratedMessageCompanion

/**
 * ChiefOfStateAggregate
 *
 * @param actorSystem    the actor system
 * @param config         config object reading the application configuration file
 * @param commandHandler the commands handler
 * @param eventHandler   the events handler
 */
class Aggregate(
    actorSystem: ActorSystem,
    config: Config,
    commandHandler: CommandHandler[State],
    eventHandler: EventHandler[State],
    protoEncryption: ProtoEncryption
) extends AggregateRoot[State](actorSystem, commandHandler, eventHandler, protoEncryption) {
  // $COVERAGE-OFF$
  override def aggregateName: String = "chiefOfState"

  override def stateCompanion: GeneratedMessageCompanion[State] = State

  // $COVERAGE-ON$
}
