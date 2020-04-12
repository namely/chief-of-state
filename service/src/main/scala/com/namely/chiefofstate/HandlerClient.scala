package com.namely.chiefofstate

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.grpc.scaladsl.AkkaGrpcClient
import com.namely.protobuf.chief_of_state.handler.{HandlerService, HandlerServiceClient}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

object HandlerClient {

  private implicit val actorSys: ActorSystem = ActorSystem("HandlerServiceClient")
  private implicit val ec: ExecutionContextExecutor = actorSys.dispatcher

  private lazy val logger = LoggerFactory.getLogger(getClass)

  private val clientSettings = GrpcClientSettings.fromConfig(HandlerService.name)
  val client: HandlerServiceClient = HandlerServiceClient(clientSettings)


  def stop(implicit client: AkkaGrpcClient): Unit = {
    client.close()
      .flatMap(_ => actorSys.terminate())
      .onComplete {
        case Success(_) => logger.info("stopped")
        case Failure(ex) => ex.printStackTrace()
      }
  }
}
