package com.namely.chiefofstate

import com.google.protobuf.any.Any
import com.namely.lagom.NamelyAggregate
import com.namely.lagom.NamelyCommandHandler
import com.namely.lagom.NamelyEventHandler
import scalapb.GeneratedMessageCompanion

object SidecarAggregate extends NamelyAggregate[Any] {

  override def aggregateName: String = "chiefOfState"

  override def commandHandler: NamelyCommandHandler[Any] = new SidecarCommandHandler

  override def eventHandler: NamelyEventHandler[Any] = new SidecarEventHandler

  override def stateCompanion: GeneratedMessageCompanion[Any] = Any
}
