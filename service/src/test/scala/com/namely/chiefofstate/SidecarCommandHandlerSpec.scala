package com.namely.chiefofstate

import akka.actor.ActorSystem
import akka.grpc.{GrpcClientSettings, GrpcServiceException}
import akka.grpc.scaladsl.Metadata
import akka.http.scaladsl.{Http, HttpConnectionContext}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import com.google.protobuf.any.Any
import com.namely.lagom.{NamelyCommand, PersistAndReplyResponse}
import com.namely.lagom.testkit.NamelyTestSpec
import com.namely.protobuf.chief_of_state.handler.{AbstractHandlerServicePowerApiRouter, HandleCommandRequest, HandleCommandResponse, HandleEventRequest, HandleEventResponse, HandlerServiceClient, HandlerServiceHandler, PersistAndReply}
import com.namely.protobuf.lagom.common.StateMeta
import com.typesafe.config.ConfigFactory
import java.util.concurrent.TimeUnit.SECONDS

import com.namely.protobuf.chief_of_state.tests.{CreateUser, UserCreated, UserState}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class SidecarCommandHandlerSpec extends NamelyTestSpec {

  "Command Handler" must {
    // command handler
    val handler = new SidecarCommandHandler

    // create server
    new HandlerServer().run()

    SECONDS.sleep(5)
    // mock handler service client
    implicit val clientSystem: ActorSystem = ActorSystem("HandlerServiceClient")
    val client = {
      implicit val ec: ExecutionContextExecutor = clientSystem.dispatcher
      HandlerServiceClient(GrpcClientSettings.connectToServiceAt("127.0.0.1", 8080))
    }

    var newClient = HandlerClient.client
    newClient = client

    "handle process command" in {
      val cmd = CreateUser()
      val state = Any.pack(UserState())
      val meta = StateMeta().withRevisionNumber(1)

      val res = handler.handle(NamelyCommand(cmd, null), state, meta)
      // FIXME: Always gets a NONE
      res.success.value shouldBe PersistAndReplyResponse(Any())
    }
  }
}

class HandlerServer() {
  implicit val clientSystem: ActorSystem = ActorSystem("HandlerServiceClient")
  def run(): Future[Http.ServerBinding] = {
    // Akka boot up code
    val conf = ConfigFactory.parseString("akka.http.server.preview.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication()).resolve()
    val sys = ActorSystem("HandlerServiceServer", conf)

    val bound = new AbstractHandlerServicePowerApiRouter(system = sys) {
      override def handleCommand(in: HandleCommandRequest, metadata: Metadata): Future[HandleCommandResponse] = {
        Future.successful(HandleCommandResponse().withPersistAndReply(PersistAndReply(event = Some(Any.pack(UserCreated())))))
      }

      override def handleEvent(in: HandleEventRequest, metadata: Metadata): Future[HandleEventResponse] = ???
    }

    implicit val ec: ExecutionContext = sys.dispatcher

    // Create service handlers
    val service: HttpRequest => Future[HttpResponse] =
      HandlerServiceHandler(bound)

    // Bind service handler servers to localhost:8080/8081
    val binding = Http().bindAndHandleAsync(
      service,
      interface = "127.0.0.1",
      port = 8080,
      connectionContext = HttpConnectionContext())

    // report successful binding
    binding.foreach { binding => println(s"gRPC server bound to: ${binding.localAddress}") }

    binding
  }
}