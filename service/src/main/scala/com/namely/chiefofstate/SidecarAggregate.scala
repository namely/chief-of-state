package com.namely.chiefofstate

import com.namely.lagom.{NamelyAggregate, NamelyCommandHandler, NamelyEventHandler}
import scalapb.GeneratedMessageCompanion
import com.google.protobuf.any.Any

object SidecarAggregate extends NamelyAggregate[Any] {

  override def aggregateName: String = "chiefOfState"

  override def commandHandler: NamelyCommandHandler[Any] = new SidecarCommandHandler

  override def eventHandler: NamelyEventHandler[Any] = new SidecarEventHandler

  override def stateCompanion: GeneratedMessageCompanion[Any] = Any
}
