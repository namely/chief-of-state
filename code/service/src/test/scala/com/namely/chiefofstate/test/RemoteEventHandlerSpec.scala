package com.namely.chiefofstate.test

import com.google.protobuf.any
import com.namely.chiefofstate.config.{GrpcClient, GrpcConfig, GrpcServer}
import com.namely.chiefofstate.test.helper.BaseSpec
import com.namely.chiefofstate.RemoteEventHandler
import com.namely.protobuf.chiefofstate.v1.persistence.StateWrapper
import com.namely.protobuf.chiefofstate.v1.tests.{Account, AccountOpened}
import com.namely.protobuf.chiefofstate.v1.writeside.{
  HandleEventRequest,
  HandleEventResponse,
  WriteSideHandlerServiceGrpc
}
import com.namely.protobuf.chiefofstate.v1.writeside.WriteSideHandlerServiceGrpc.WriteSideHandlerServiceBlockingStub
import io.grpc.{ManagedChannel, Status}
import io.grpc.netty.NettyChannelBuilder
import org.grpcmock.GrpcMock
import org.grpcmock.GrpcMock._

import scala.util.Try

class RemoteEventHandlerSpec extends BaseSpec {

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

      stubFor(
        unaryMethod(WriteSideHandlerServiceGrpc.METHOD_HANDLE_EVENT)
          .withRequest(request)
          .willReturn(
            response(expected)
          )
      )

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

      stubFor(
        unaryMethod(WriteSideHandlerServiceGrpc.METHOD_HANDLE_EVENT)
          .withRequest(request)
          .willReturn(
            statusException(Status.UNKNOWN)
          )
      )

      val writeHandlerServicetub: WriteSideHandlerServiceBlockingStub =
        new WriteSideHandlerServiceBlockingStub(serverChannel)

      val remoteEventHandler: RemoteEventHandler = RemoteEventHandler(grpcConfig, writeHandlerServicetub)
      val triedHandleEventResponse: Try[HandleEventResponse] = remoteEventHandler.handleEvent(event, stateWrapper)
      (triedHandleEventResponse.failure.exception should have).message("UNKNOWN")
    }
  }
}
