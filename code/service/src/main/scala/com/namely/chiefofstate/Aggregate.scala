package com.namely.chiefofstate

import akka.actor.ActorSystem
import com.google.protobuf.empty.Empty
import com.typesafe.config.Config
import io.superflat.lagompb.encryption.EncryptionAdapter
import io.superflat.lagompb.{AggregateRoot, CommandHandler, EventHandler}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import io.superflat.lagompb.Command
import io.superflat.lagompb.protobuf.v1.core.{EventWrapper, StateWrapper}
import com.namely.chiefofstate.persistence.{CosEventAdapter, CosSnapshotAdapter}

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

  override def aggregateName: String = "chiefOfState"

  /**
   * generate the lagom-pb event-sourced behavior and inject event and
   * snapshot adapters
   *
   * @param persistenceId aggregate persistence ID
   * @return EventSourcedBehavior
   */
  override def create(persistenceId: PersistenceId): EventSourcedBehavior[Command, EventWrapper, StateWrapper] = {
    super
      .create(persistenceId)
      .eventAdapter(CosEventAdapter)
      .snapshotAdapter(CosSnapshotAdapter)
  }
}
