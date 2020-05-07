package com.namely.chiefofstate

import akka.actor.ActorSystem
import com.google.protobuf.any.Any
import com.namely.lagom.NamelyAggregate
import com.namely.lagom.NamelyCommandHandler
import com.namely.lagom.NamelyEventHandler
import com.typesafe.config.Config
import scalapb.GeneratedMessageCompanion

class ChiefOfStateAggregate(
    actorSystem: ActorSystem,
    config: Config,
    commandHandler: NamelyCommandHandler[Any],
    eventHandler: NamelyEventHandler[Any]
) extends NamelyAggregate[Any](actorSystem, config, commandHandler, eventHandler) {

  override def aggregateName: String = "chiefOfState"

  override def stateCompanion: GeneratedMessageCompanion[Any] = Any
}
