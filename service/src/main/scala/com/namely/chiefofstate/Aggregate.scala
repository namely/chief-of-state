package com.namely.chiefofstate

import akka.actor.ActorSystem
import com.google.protobuf.empty.Empty
import com.typesafe.config.Config
import io.superflat.lagompb.{AggregateRoot, CommandHandler, EventHandler}
import io.superflat.lagompb.encryption.EncryptionAdapter
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
  commandHandler: CommandHandler,
  eventHandler: EventHandler,
  encryptionAdapter: EncryptionAdapter
) extends AggregateRoot(actorSystem, commandHandler, eventHandler, Empty.defaultInstance, encryptionAdapter) {
  // $COVERAGE-OFF$
  override def aggregateName: String = "chiefOfState"

  // $COVERAGE-ON$
}
