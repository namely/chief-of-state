package com.namely.chiefofstate

import com.google.protobuf.any
import com.namely.chiefofstate.config.{GrpcClient, GrpcConfig, GrpcServer}
import com.namely.chiefofstate.helper.BaseSpec
import com.namely.protobuf.chiefofstate.v1.persistence.StateWrapper
import com.namely.protobuf.chiefofstate.v1.tests.{Account, AccountOpened}
import com.namely.protobuf.chiefofstate.v1.writeside.{
  HandleEventRequest,
  HandleEventResponse,
  WriteSideHandlerServiceGrpc
}
import com.namely.protobuf.chiefofstate.v1.writeside.WriteSideHandlerServiceGrpc.WriteSideHandlerServiceBlockingStub
import io.grpc.{ManagedChannel, Status}
import scala.util.Try
import com.namely.chiefofstate.helper.GrpcHelpers.Closeables
import io.grpc.ServerServiceDefinition
import io.grpc.inprocess._
import scala.concurrent.ExecutionContext.global

class RemoteEventHandlerSpec extends BaseSpec {

  val grpcConfig: GrpcConfig = GrpcConfig(GrpcClient(5000), GrpcServer("0.0.0.0", 5051))

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

  "RemoteEventHandler" should {
    "handle event successfully" in {
      val state: Account = Account().withAccountUuid("123")

      val stateWrapper: StateWrapper = StateWrapper().withState(com.google.protobuf.any.Any.pack(state))

      val resultingState = com.google.protobuf.any.Any.pack(state.withBalance(200))

      val event: any.Any = com.google.protobuf.any.Any.pack(AccountOpened())

      val expected: HandleEventResponse =
        HandleEventResponse().withResultingState(resultingState)

      val request: HandleEventRequest = HandleEventRequest()
        .withPriorState(stateWrapper.getState)
        .withEventMeta(stateWrapper.getMeta)
        .withEvent(event)

      val serviceImpl = mock[WriteSideHandlerServiceGrpc.WriteSideHandlerService]
      (serviceImpl.handleEvent _)
        .expects(request)
        .returning(scala.concurrent.Future.successful(expected))

      val service = WriteSideHandlerServiceGrpc.bindService(serviceImpl, global)
      val serverName = InProcessServerBuilder.generateName()
      createServer(serverName, service)
      val serverChannel = getChannel(serverName)

      val writeHandlerServicetub: WriteSideHandlerServiceBlockingStub =
        new WriteSideHandlerServiceBlockingStub(serverChannel)

      val remoteEventHandler: RemoteEventHandler = RemoteEventHandler(grpcConfig, writeHandlerServicetub)
      val triedHandleEventResponse: Try[HandleEventResponse] = remoteEventHandler.handleEvent(event, stateWrapper)
      triedHandleEventResponse.success.value shouldBe (expected)
    }

    "handle event when there is a failure" in {
      val state: Account = Account().withAccountUuid("123")

      val stateWrapper: StateWrapper = StateWrapper().withState(com.google.protobuf.any.Any.pack(state))

      val event: any.Any = com.google.protobuf.any.Any.pack(AccountOpened())

      val request: HandleEventRequest = HandleEventRequest()
        .withPriorState(stateWrapper.getState)
        .withEventMeta(stateWrapper.getMeta)
        .withEvent(event)

      val serviceImpl = mock[WriteSideHandlerServiceGrpc.WriteSideHandlerService]
      (serviceImpl.handleEvent _)
        .expects(request)
        .returning(scala.concurrent.Future.failed(Status.UNKNOWN.asException()))

      val service = WriteSideHandlerServiceGrpc.bindService(serviceImpl, global)
      val serverName = InProcessServerBuilder.generateName()
      createServer(serverName, service)
      val serverChannel = getChannel(serverName)

      val writeHandlerServicetub: WriteSideHandlerServiceBlockingStub =
        new WriteSideHandlerServiceBlockingStub(serverChannel)

      val remoteEventHandler: RemoteEventHandler = RemoteEventHandler(grpcConfig, writeHandlerServicetub)
      val triedHandleEventResponse: Try[HandleEventResponse] = remoteEventHandler.handleEvent(event, stateWrapper)
      (triedHandleEventResponse.failure.exception should have).message("UNKNOWN")
    }
  }
}
