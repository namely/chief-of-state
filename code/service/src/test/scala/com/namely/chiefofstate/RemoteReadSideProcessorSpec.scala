/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

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
import io.grpc.Status
import io.grpc.inprocess._

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class RemoteReadSideProcessorSpec extends BaseSpec {

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

      // mock the grpc server
      val mockImpl = mock[ReadSideHandlerServiceGrpc.ReadSideHandlerService]

      (mockImpl.handleReadSide _)
        .expects(request)
        .returning(Future.successful(expected))

      val service = ReadSideHandlerServiceGrpc.bindService(mockImpl, global)

      val serverName = InProcessServerBuilder.generateName()

      // register a server that intercepts traces and reports errors
      closeables.register(
        InProcessServerBuilder
          .forName(serverName)
          .directExecutor()
          .addService(service)
          .build()
          .start()
      )

      val serverChannel = {
        closeables.register(
          InProcessChannelBuilder
            .forName(serverName)
            .directExecutor()
            .build()
        )
      }

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

      val mockImpl = mock[ReadSideHandlerServiceGrpc.ReadSideHandlerService]

      (mockImpl.handleReadSide _)
        .expects(request)
        .returning(Future.failed(Status.INTERNAL.asException()))

      val service = ReadSideHandlerServiceGrpc.bindService(mockImpl, global)

      val serverName = InProcessServerBuilder.generateName()

      // register a server that intercepts traces and reports errors
      closeables.register(
        InProcessServerBuilder
          .forName(serverName)
          .directExecutor()
          .addService(service)
          .build()
          .start()
      )

      val serverChannel = {
        closeables.register(
          InProcessChannelBuilder
            .forName(serverName)
            .directExecutor()
            .build()
        )
      }

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
