package com.namely.chiefofstate

import com.google.protobuf.any.Any
import com.namely.lagom.{NamelyEventHandler, NamelyException}
import com.namely.protobuf.chief_of_state.handler.HandleEventRequest
import com.namely.protobuf.lagom.common.StateMeta
import scalapb.GeneratedMessage

import scala.util.{Failure, Success, Try}

class SidecarEventHandler extends NamelyEventHandler[Any] {
  override def handle(event: GeneratedMessage, state: Any): Any = {

    val meta = Any.pack(StateMeta())

    val handleEventRequest = HandleEventRequest()
        .withEvent(Any.pack(event))
        .withCurrentState(state)
        .withMeta(meta)

    Try(HandlerClient.client.handleEvent(handleEventRequest)) match {
      case Failure(e) => throw new NotImplementedError(e.getMessage)
      case Success(value) => value.value match {
        case Some(value) => Any.pack(value.get)
        case None => throw new NamelyException(s"unable to handle event ${event.companion.getClass.getCanonicalName}")
      }
    }
  }
}
