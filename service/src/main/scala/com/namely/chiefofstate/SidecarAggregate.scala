package com.namely.chiefofstate

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import com.google.protobuf.any.Any
import com.namely.lagom.NamelyAggregate
import com.namely.lagom.NamelyCommandHandler
import com.namely.lagom.NamelyEventHandler
import com.namely.protobuf.chief_of_state.handler.{HandlerService, HandlerServiceClient}
import scalapb.GeneratedMessageCompanion
import scala.concurrent.ExecutionContext.Implicits.global

class SidecarAggregate(actorSystem: ActorSystem) extends NamelyAggregate[Any](actorSystem) {

  implicit val sys: ActorSystem = actorSystem
  private lazy val settings = GrpcClientSettings.fromConfig(HandlerService.name)

  val handlerServiceClient: HandlerServiceClient = HandlerServiceClient(settings)

  override def aggregateName: String = "chiefOfState"

  override def commandHandler: NamelyCommandHandler[Any] = new SidecarCommandHandler(actorSystem, handlerServiceClient)

  override def eventHandler: NamelyEventHandler[Any] = new SidecarEventHandler(actorSystem, handlerServiceClient)

  override def stateCompanion: GeneratedMessageCompanion[Any] = Any
}
