package com.namely.chiefofstate

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.grpc.scaladsl.AkkaGrpcClient
import com.namely.protobuf.chief_of_state.handler.HandlerServiceClient
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success, Try}

object HandlerClient {

  private implicit val actorSys: ActorSystem = ActorSystem("HandlerServiceClient")
  private implicit val ec: ExecutionContextExecutor = actorSys.dispatcher

  private lazy val logger = LoggerFactory.getLogger(getClass)

  // FIXME: Might be better to pass this through a .conf file
  val host: String = Try(sys.env("HANDLER_SERVICE_HOSTNAME")).getOrElse("localhost")
  val port: Int = Try(sys.env("HANDLER_SERVICE_PORT").toInt).getOrElse(8080) // what is the default for akka grpc??
  val useTLS: Boolean = Try(sys.env("USE_TLS").toBoolean).getOrElse(false)

  private val clientSettings = GrpcClientSettings.connectToServiceAt(host = host, port = port).withTls(useTLS)

  val client: HandlerServiceClient = HandlerServiceClient(clientSettings)


  def stop(implicit client: AkkaGrpcClient): Unit = {
    client.close()
      .flatMap(_ => actorSys.terminate())
      .onComplete {
        case Success(_) => logger.info("stopped")
        case Failure(ex) => logger.debug(ex.getMessage)
      }
  }
}
