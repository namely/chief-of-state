package com.namely.chiefofstate

import com.namely.chiefofstate.helper.BaseSpec
import com.namely.protobuf.chiefofstate.v1.common.MetaData
import com.namely.protobuf.chiefofstate.v1.readside.{
  HandleReadSideRequest,
  HandleReadSideResponse,
  ReadSideHandlerServiceGrpc
}
import com.namely.protobuf.chiefofstate.v1.readside.ReadSideHandlerServiceGrpc.ReadSideHandlerServiceBlockingStub
import com.namely.protobuf.chiefofstate.v1.tests.{Account, AccountOpened}
import io.grpc.{ManagedChannel, Status}
import io.grpc.netty.NettyChannelBuilder
import org.grpcmock.GrpcMock
import org.grpcmock.GrpcMock._

class RemoteReadSideProcessorSpec extends BaseSpec {
  var serverChannel: ManagedChannel = null

  override def beforeAll(): Unit = {
    GrpcMock.configureFor(grpcMock(50053).build().start())
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

  "RemoteReadSideProcessor" should {
    "handle events as expected" in {
      val accountOpened = AccountOpened()
      val account = Account()
      val eventTag = "chiefofstate8"
      val resultingState =
        com.google.protobuf.any.Any.pack(account.withBalance(200))

      val meta: MetaData = MetaData().withEntityId("231")

      val request: HandleReadSideRequest = HandleReadSideRequest()
        .withEvent(com.google.protobuf.any.Any.pack(accountOpened))
        .withState(resultingState)
        .withMeta(meta)

      val expected: HandleReadSideResponse = HandleReadSideResponse().withSuccessful(true)

      stubFor(
        unaryMethod(ReadSideHandlerServiceGrpc.METHOD_HANDLE_READ_SIDE)
          .withRequest(request)
          .withHeader("x-cos-entity-id", "231")
          .withHeader("x-cos-event-tag", eventTag)
          .willReturn(
            response(expected)
          )
      )

      val readSideHandlerServiceStub: ReadSideHandlerServiceBlockingStub =
        new ReadSideHandlerServiceBlockingStub(serverChannel)
      val remoteReadSideProcessor = new RemoteReadSideProcessor(readSideHandlerServiceStub)
      val triedHandleReadSideResponse =
        remoteReadSideProcessor.processEvent(com.google.protobuf.any.Any.pack(accountOpened),
                                             eventTag,
                                             resultingState,
                                             meta
        )

      triedHandleReadSideResponse.success.value shouldBe (expected)
    }

    "handle event when there is an exception" in {
      val accountOpened = AccountOpened()
      val account = Account()
      val eventTag = "chiefofstate8"
      val resultingState =
        com.google.protobuf.any.Any.pack(account.withBalance(200))

      val meta: MetaData = MetaData().withEntityId("231")

      val request: HandleReadSideRequest = HandleReadSideRequest()
        .withEvent(com.google.protobuf.any.Any.pack(accountOpened))
        .withState(resultingState)
        .withMeta(meta)

      stubFor(
        unaryMethod(ReadSideHandlerServiceGrpc.METHOD_HANDLE_READ_SIDE)
          .withRequest(request)
          .withHeader("x-cos-entity-id", "231")
          .withHeader("x-cos-event-tag", eventTag)
          .willReturn(
            statusException(Status.INTERNAL)
          )
      )

      val readSideHandlerServiceStub: ReadSideHandlerServiceBlockingStub =
        new ReadSideHandlerServiceBlockingStub(serverChannel)
      val remoteReadSideProcessor = new RemoteReadSideProcessor(readSideHandlerServiceStub)
      val triedHandleReadSideResponse =
        remoteReadSideProcessor.processEvent(com.google.protobuf.any.Any.pack(accountOpened),
                                             eventTag,
                                             resultingState,
                                             meta
        )

      (triedHandleReadSideResponse.failure.exception should have).message("INTERNAL")
    }
  }
}
