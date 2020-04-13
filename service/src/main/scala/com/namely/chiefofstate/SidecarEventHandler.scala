package com.namely.chiefofstate

import java.time.Instant

import akka.actor.ActorSystem
import com.google.protobuf.any.Any
import com.namely.lagom.NamelyEventHandler
import com.namely.lagom.NamelyException
import com.namely.lagom.util.NamelyTimestamps
import com.namely.protobuf.chief_of_state.handler.{HandleEventRequest, HandlerServiceClient}
import com.namely.protobuf.lagom.common.StateMeta
import scalapb.GeneratedMessage

import scala.util.Failure
import scala.util.Success
import scala.util.Try

class SidecarEventHandler(actorSystem: ActorSystem, akkaGrpcClient: HandlerServiceClient) extends NamelyEventHandler[Any](actorSystem) {

  lazy val handlerServiceClient: HandlerServiceClient = akkaGrpcClient

  override def handle(event: GeneratedMessage, state: Any): Any = {

    // FIXME: Revision Number
    val meta = Any.pack(
      StateMeta()
        .withRevisionDate(NamelyTimestamps.Instants(Instant.now).toTimestamp)
        .withRevisionNumber(1)
    )

    val handleEventRequest = HandleEventRequest()
      .withEvent(Any.pack(event))
      .withCurrentState(state)
      .withMeta(meta)

    Try(
      handlerServiceClient.handleEvent(handleEventRequest)
    ) match {

      case Failure(e) =>
        // NOTE: close the grpc client to avoid leaking???
        throw new NotImplementedError(e.getMessage)

      case Success(value) =>
        value.value match {
          case Some(value) => Any.pack(value.get)
          case None =>
            // NOTE: close the grpc client to avoid leaking???
            throw new NamelyException(s"unable to handle event ${event.companion.getClass.getCanonicalName}")
        }
    }
  }
}
