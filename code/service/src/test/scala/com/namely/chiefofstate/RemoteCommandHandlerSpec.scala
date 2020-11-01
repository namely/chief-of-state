package com.namely.chiefofstate.test

import com.google.protobuf.any.Any
import com.google.protobuf.ByteString
import com.namely.chiefofstate.RemoteCommandHandler
import com.namely.chiefofstate.config.{GrpcClient, GrpcConfig, GrpcServer}
import com.namely.chiefofstate.helper.BaseSpec
import com.namely.protobuf.chiefofstate.v1.internal.RemoteCommand
import com.namely.protobuf.chiefofstate.v1.internal.RemoteCommand.Header.Value
import com.namely.protobuf.chiefofstate.v1.persistence.StateWrapper
import com.namely.protobuf.chiefofstate.v1.tests.{Account, AccountOpened, OpenAccount}
import com.namely.protobuf.chiefofstate.v1.writeside.{
  HandleCommandRequest,
  HandleCommandResponse,
  WriteSideHandlerServiceGrpc
}
import com.namely.protobuf.chiefofstate.v1.writeside.WriteSideHandlerServiceGrpc.WriteSideHandlerServiceBlockingStub
import io.grpc.{ManagedChannel, Metadata, Status}
import io.grpc.netty.NettyChannelBuilder
import org.grpcmock.GrpcMock
import org.grpcmock.GrpcMock._

import scala.util.Try

class RemoteCommandHandlerSpec extends BaseSpec {

  var serverChannel: ManagedChannel = null
  val grpcConfig: GrpcConfig = GrpcConfig(GrpcClient(5000), GrpcServer(5052))

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
      val state = Account().withAccountUuid("123")
      val stateWrapper: StateWrapper = StateWrapper().withState(com.google.protobuf.any.Any.pack(state))
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
          .withHeader("header-1", "header-value-1")
          .willReturn(
            response(expected)
          )
      )

      val writeHandlerServicetub: WriteSideHandlerServiceBlockingStub =
        new WriteSideHandlerServiceBlockingStub(serverChannel)

      val remoteCommand = RemoteCommand()
        .withCommand(command)
        .withHeaders(
          Seq(
            RemoteCommand.Header().withKey("header-1").withStringValue("header-value-1")
          )
        )

      val remoteCommandHandler: RemoteCommandHandler = RemoteCommandHandler(grpcConfig, writeHandlerServicetub)
      val triedHandleCommandResponse: Try[HandleCommandResponse] =
        remoteCommandHandler.handleCommand(remoteCommand, stateWrapper)
      triedHandleCommandResponse.success.value shouldBe (expected)
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
          .withHeader("header-1", "header-value-1")
          .withHeader(Metadata.Key.of("header-2-bin", Metadata.BINARY_BYTE_MARSHALLER), "header-value-2".getBytes)
          .willReturn(
            statusException(Status.INTERNAL)
          )
      )

      val writeHandlerServicetub: WriteSideHandlerServiceBlockingStub =
        new WriteSideHandlerServiceBlockingStub(serverChannel)

      val remoteCommand = RemoteCommand()
        .withCommand(command)
        .withHeaders(
          Seq(
            RemoteCommand.Header().withKey("header-1").withStringValue("header-value-1"),
            RemoteCommand
              .Header()
              .withKey("header-2-bin")
              .withBytesValue(ByteString.copyFrom("header-value-2".getBytes))
          )
        )

      val remoteCommandHandler: RemoteCommandHandler = RemoteCommandHandler(grpcConfig, writeHandlerServicetub)
      val triedHandleCommandResponse: Try[HandleCommandResponse] =
        remoteCommandHandler.handleCommand(remoteCommand, stateWrapper)
      (triedHandleCommandResponse.failure.exception should have).message("INTERNAL")
    }

    "handle command when a header is not properly set" in {
      val stateWrapper: StateWrapper = StateWrapper()
      val command: Any = Any.pack(OpenAccount())

      val request: HandleCommandRequest = HandleCommandRequest()
        .withCommand(command)
        .withPriorState(stateWrapper.getState)
        .withPriorEventMeta(stateWrapper.getMeta)

      stubFor(
        unaryMethod(WriteSideHandlerServiceGrpc.METHOD_HANDLE_COMMAND)
          .withRequest(request)
          .withHeader("header-1", "header-value-1")
          .withHeader(Metadata.Key.of("header-2-bin", Metadata.BINARY_BYTE_MARSHALLER), "header-value-2".getBytes)
          .willReturn(
            statusException(Status.INTERNAL)
          )
      )

      val writeHandlerServicetub: WriteSideHandlerServiceBlockingStub =
        new WriteSideHandlerServiceBlockingStub(serverChannel)

      val remoteCommand = RemoteCommand()
        .withCommand(command)
        .withHeaders(
          Seq(
            RemoteCommand.Header().withKey("header-1").withStringValue("header-value-1"),
            RemoteCommand.Header().withKey("header-2").withValue(Value.Empty)
          )
        )

      val remoteCommandHandler: RemoteCommandHandler = RemoteCommandHandler(grpcConfig, writeHandlerServicetub)
      val triedHandleCommandResponse: Try[HandleCommandResponse] =
        remoteCommandHandler.handleCommand(remoteCommand, stateWrapper)
      (triedHandleCommandResponse.failure.exception should have).message("header value must be string or bytes")
    }
  }
}
