package com.namely.chiefofstate.test

import com.google.protobuf.any.Any
import com.namely.chiefofstate.config.{GrpcClient, GrpcConfig, GrpcServer}
import com.namely.chiefofstate.test.helper.BaseSpec
import com.namely.chiefofstate.RemoteCommandHandler
import com.namely.protobuf.chiefofstate.v1.persistence.StateWrapper
import com.namely.protobuf.chiefofstate.v1.tests.{AccountOpened, OpenAccount}
import com.namely.protobuf.chiefofstate.v1.writeside.{
  HandleCommandRequest,
  HandleCommandResponse,
  WriteSideHandlerServiceGrpc
}
import com.namely.protobuf.chiefofstate.v1.writeside.WriteSideHandlerServiceGrpc.WriteSideHandlerServiceBlockingStub
import io.grpc.{ManagedChannel, Status}
import io.grpc.netty.NettyChannelBuilder
import org.grpcmock.GrpcMock
import org.grpcmock.GrpcMock._

import scala.util.Try

class RemoteCommandHandlerSpec extends BaseSpec {

  var serverChannel: ManagedChannel = null
  val grpcConfig: GrpcConfig = GrpcConfig(GrpcClient(5000), GrpcServer(5051))

  override def beforeAll(): Unit = {
    GrpcMock.configureFor(grpcMock(grpcConfig.server.port).build().start())
  }

  override def beforeEach(): Unit = {
    GrpcMock.resetMappings()
    serverChannel = NettyChannelBuilder
      .forAddress("localhost", getGlobalPort)
      .usePlaintext()
      .build()
  }

  override def afterEach(): Unit = {
    serverChannel.shutdownNow()
  }

  "RemoteCommandHandler" should {
    "handle command successful" in {
      val stateWrapper: StateWrapper = StateWrapper()
      val command: Any = Any.pack(OpenAccount())

      val event: AccountOpened = AccountOpened()
      val expected: HandleCommandResponse = HandleCommandResponse().withEvent(Any.pack(event))

      val request: HandleCommandRequest = HandleCommandRequest()
        .withCommand(command)
        .withPriorState(stateWrapper.getState)
        .withPriorEventMeta(stateWrapper.getMeta)

      stubFor(
        unaryMethod(WriteSideHandlerServiceGrpc.METHOD_HANDLE_COMMAND)
          .withRequest(request)
          .willReturn(
            response(expected)
          )
      )

      val writeHandlerServicetub: WriteSideHandlerServiceBlockingStub =
        new WriteSideHandlerServiceBlockingStub(serverChannel)

      val remoteCommandHandler: RemoteCommandHandler = RemoteCommandHandler(grpcConfig, writeHandlerServicetub)
      val handleCommandResponse: Try[HandleCommandResponse] = remoteCommandHandler.handleCommand(command, stateWrapper)
      handleCommandResponse.success.value shouldBe (expected)
    }

    "handle command when there is an exception" in {
      val stateWrapper: StateWrapper = StateWrapper()
      val command: Any = Any.pack(OpenAccount())

      val request: HandleCommandRequest = HandleCommandRequest()
        .withCommand(command)
        .withPriorState(stateWrapper.getState)
        .withPriorEventMeta(stateWrapper.getMeta)

      stubFor(
        unaryMethod(WriteSideHandlerServiceGrpc.METHOD_HANDLE_COMMAND)
          .withRequest(request)
          .willReturn(
            statusException(Status.INTERNAL)
          )
      )

      val writeHandlerServicetub: WriteSideHandlerServiceBlockingStub =
        new WriteSideHandlerServiceBlockingStub(serverChannel)

      val remoteCommandHandler: RemoteCommandHandler = RemoteCommandHandler(grpcConfig, writeHandlerServicetub)
      val handleCommandResponse: Try[HandleCommandResponse] = remoteCommandHandler.handleCommand(command, stateWrapper)
      (handleCommandResponse.failure.exception should have).message("INTERNAL")
    }
  }
}
