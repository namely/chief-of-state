package com.namely.chiefofstate

import akka.actor.ActorSystem
import com.namely.lagom.NamelyAggregate
import com.namely.lagom.NamelyCommandHandler
import com.namely.lagom.NamelyEventHandler
import com.namely.protobuf.chief_of_state.persistence.State
import com.typesafe.config.Config
import scalapb.GeneratedMessageCompanion

class ChiefOfStateAggregate(
    actorSystem: ActorSystem,
    config: Config,
    commandHandler: NamelyCommandHandler[State],
    eventHandler: NamelyEventHandler[State]
) extends NamelyAggregate[State](actorSystem, config, commandHandler, eventHandler) {

  override def aggregateName: String = "chiefOfState"

  override def stateCompanion: GeneratedMessageCompanion[State] = State
}
