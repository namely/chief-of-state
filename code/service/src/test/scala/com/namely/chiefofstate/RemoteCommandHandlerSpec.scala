package com.namely.chiefofstate

import com.google.protobuf.any.Any
import com.google.protobuf.ByteString
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
import scala.util.Try
import com.namely.chiefofstate.helper.GrpcHelpers.Closeables
import io.grpc.inprocess._
import io.grpc.ServerServiceDefinition
import scala.concurrent.ExecutionContext.global

class RemoteCommandHandlerSpec extends BaseSpec {

  val grpcConfig: GrpcConfig = GrpcConfig(GrpcClient(5000), GrpcServer("0.0.0.0", 5052))

  // define set of resources to close after each test
  val closeables: Closeables = new Closeables()

  // register a server that intercepts traces and reports errors
  def createServer(serverName: String, service: ServerServiceDefinition): Unit = {
    closeables.register(
      InProcessServerBuilder
        .forName(serverName)
        .directExecutor()
        .addService(service)
        .build()
        .start()
    )
  }

  def getChannel(serverName: String): ManagedChannel = {
    closeables.register(
      InProcessChannelBuilder
        .forName(serverName)
        .directExecutor()
        .build()
    )
  }

  override def afterEach(): Unit = {
    closeables.closeAll()
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

      val serviceImpl = mock[WriteSideHandlerServiceGrpc.WriteSideHandlerService]

      (serviceImpl.handleCommand _)
        .expects(request)
        .returning(scala.concurrent.Future.successful(expected))

      val service = WriteSideHandlerServiceGrpc.bindService(serviceImpl, global)
      val serverName = InProcessServerBuilder.generateName()
      createServer(serverName, service)
      val serverChannel = getChannel(serverName)

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

      val serviceImpl = mock[WriteSideHandlerServiceGrpc.WriteSideHandlerService]

      (serviceImpl.handleCommand _)
        .expects(request)
        .returning(scala.concurrent.Future.failed(Status.INTERNAL.asException()))

      val service = WriteSideHandlerServiceGrpc.bindService(serviceImpl, global)
      val serverName = InProcessServerBuilder.generateName()
      createServer(serverName, service)
      val serverChannel = getChannel(serverName)

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
      val serviceImpl = mock[WriteSideHandlerServiceGrpc.WriteSideHandlerService]
      val service = WriteSideHandlerServiceGrpc.bindService(serviceImpl, global)
      val serverName = InProcessServerBuilder.generateName()
      createServer(serverName, service)
      val serverChannel = getChannel(serverName)

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
