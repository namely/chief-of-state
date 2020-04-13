package com.namely.chiefofstate

import akka.stream.Materializer
import com.google.protobuf.any.Any
import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
import com.lightbend.lagom.scaladsl.testkit.ServiceTest
import com.lightbend.lagom.scaladsl.testkit.grpc.AkkaGrpcClientHelpers
import com.namely.lagom.testkit.NamelyTestSpec
import com.namely.lagom.{NamelyCommand, PersistAndReplyResponse}
import com.namely.protobuf.chief_of_state.handler.HandlerServiceClient
import com.namely.protobuf.chief_of_state.tests.{CreateUser, UserState}
import com.namely.protobuf.lagom.common.StateMeta

import scala.concurrent.ExecutionContext.Implicits.global

class SidecarCommandHandlerSpec extends NamelyTestSpec {

  private val server: ServiceTest.TestServer[SidecarApplication with LocalServiceLocator] = ServiceTest.startServer(
    ServiceTest.defaultSetup.withSsl(true).withJdbc()
  ) { ctx =>
    new SidecarApplication(ctx) with LocalServiceLocator
  }

  implicit val mat: Materializer = server.materializer


  val grpcClient: HandlerServiceClient = AkkaGrpcClientHelpers.grpcClient(
    server,
    HandlerServiceClient.apply,
  )

  "Command Handler" must {
    // command handler
    val handler = new SidecarCommandHandler(null, grpcClient)

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

//class HandlerServer(actorSystem: ActorSystem) {
//
//  implicit val clientSystem: ActorSystem = actorSystem
//  def run(): Future[Http.ServerBinding] = {
//    // Akka boot up code
//    val conf = ConfigFactory.parseString("akka.http.server.preview.enable-http2 = on")
//      .withFallback(ConfigFactory.defaultApplication()).resolve()
//    val sys = ActorSystem("HandlerServiceServer", conf)
//
//    val bound = new AbstractHandlerServicePowerApiRouter(system = sys) {
//      override def handleCommand(in: HandleCommandRequest, metadata: Metadata): Future[HandleCommandResponse] = {
//        Future.successful(HandleCommandResponse().withPersistAndReply(PersistAndReply(event = Some(Any.pack(UserCreated())))))
//      }
//
//      override def handleEvent(in: HandleEventRequest, metadata: Metadata): Future[HandleEventResponse] = ???
//    }
//
//    implicit val ec: ExecutionContext = sys.dispatcher
//
//    // Create service handlers
//    val service: HttpRequest => Future[HttpResponse] =
//      HandlerServiceHandler(bound)
//
//    // Bind service handler servers to localhost:8080/8081
//    val binding = Http().bindAndHandleAsync(
//      service,
//      interface = "127.0.0.1",
//      port = 8080,
//      connectionContext = HttpConnectionContext())
//
//    // report successful binding
//    binding.foreach { binding => println(s"gRPC server bound to: ${binding.localAddress}") }
//
//    binding
//  }
//}