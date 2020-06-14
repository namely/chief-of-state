package com.namely.chiefofstate

import java.util.UUID

import akka.Done
import akka.grpc.GrpcServiceException
import com.google.protobuf.any.Any
import com.namely.lagom.testkit.NamelyTestSpec
import com.namely.lagom.{NamelyException, NamelyState}
import com.namely.protobuf.chief_of_state.handler.{HandleReadSideRequest, HandleReadSideResponse, HandlerServiceClient}
import com.namely.protobuf.chief_of_state.persistence.{Event, State}
import com.namely.protobuf.chief_of_state.tests.{Account, AccountOpened}
import com.namely.protobuf.lagom.common.EventMeta
import io.grpc.Status
import org.scalamock.scalatest.MockFactory
import slick.dbio.DBIO

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ChiefOfStateReadProcessorSpec extends NamelyTestSpec with MockFactory {

  "Chief-Of-State ReadSide Processor" should {

    "handle events and state as expected when response was successful" in {
      val state: State = State.defaultInstance
      val eventMeta: EventMeta = EventMeta.defaultInstance
      val accouuntId: String = UUID.randomUUID.toString
      val accountNumber: String = "123445"

      val stateProto: String = ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance))
      val eventsProtos: Seq[String] =
        Seq(ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))

      val handlerSetting: ChiefOfStateHandlerSetting = ChiefOfStateHandlerSetting(stateProto, eventsProtos)

      val event = AccountOpened()
        .withAccountNumber(accountNumber)
        .withAccountUuid(accouuntId)

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[HandlerServiceClient]

      (mockGrpcClient
        .handleReadSide(_: HandleReadSideRequest))
        .expects(
          HandleReadSideRequest()
            .withEvent(Any.pack(event))
            .withState(state.getCurrentState)
            .withMeta(Any.pack(eventMeta))
        )
        .returning(
          Future.successful(
            HandleReadSideResponse()
              .withSuccessful(true)
          )
        )

      val readSideProcessor = new ChiefOfStateReadProcessor(null, mockGrpcClient, handlerSetting, null)
      val result: DBIO[Done] =
        readSideProcessor.handle(Event().withEvent(Any.pack(event)), NamelyState(state, eventMeta))
      result.map(r => r shouldBe (Done))
    }

    "handle events and state as expected when response was not successful" in {
      val state: State = State.defaultInstance
      val eventMeta: EventMeta = EventMeta.defaultInstance
      val accouuntId: String = UUID.randomUUID.toString
      val accountNumber: String = "123445"

      val stateProto: String = ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance))
      val eventsProtos: Seq[String] =
        Seq(ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))

      val handlerSetting: ChiefOfStateHandlerSetting = ChiefOfStateHandlerSetting(stateProto, eventsProtos)

      val event = AccountOpened()
        .withAccountNumber(accountNumber)
        .withAccountUuid(accouuntId)

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[HandlerServiceClient]

      (mockGrpcClient
        .handleReadSide(_: HandleReadSideRequest))
        .expects(
          HandleReadSideRequest()
            .withEvent(Any.pack(event))
            .withState(state.getCurrentState)
            .withMeta(Any.pack(eventMeta))
        )
        .returning(
          Future.successful(
            HandleReadSideResponse()
              .withSuccessful(false)
          )
        )

      val readSideProcessor = new ChiefOfStateReadProcessor(null, mockGrpcClient, handlerSetting, null)
      an[NamelyException] shouldBe thrownBy(
        readSideProcessor.handle(Event().withEvent(Any.pack(event)), NamelyState(state, eventMeta))
      )
    }

    "handle events and state as expected when handler failed" in {
      val state: State = State.defaultInstance
      val eventMeta: EventMeta = EventMeta.defaultInstance
      val accouuntId: String = UUID.randomUUID.toString
      val accountNumber: String = "123445"

      val stateProto: String = ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance))
      val eventsProtos: Seq[String] =
        Seq(ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))

      val handlerSetting: ChiefOfStateHandlerSetting = ChiefOfStateHandlerSetting(stateProto, eventsProtos)

      val event = AccountOpened()
        .withAccountNumber(accountNumber)
        .withAccountUuid(accouuntId)

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[HandlerServiceClient]

      (mockGrpcClient
        .handleReadSide(_: HandleReadSideRequest))
        .expects(
          HandleReadSideRequest()
            .withEvent(Any.pack(event))
            .withState(state.getCurrentState)
            .withMeta(Any.pack(eventMeta))
        )
        .throws(new RuntimeException("broken"))

      val readSideProcessor = new ChiefOfStateReadProcessor(null, mockGrpcClient, handlerSetting, null)
      an[NamelyException] shouldBe thrownBy(
        readSideProcessor.handle(Event().withEvent(Any.pack(event)), NamelyState(state, eventMeta))
      )
    }

    "handle failed response as expected" in {
      val state: State = State.defaultInstance
      val eventMeta: EventMeta = EventMeta.defaultInstance
      val accouuntId: String = UUID.randomUUID.toString
      val accountNumber: String = "123445"

      val stateProto: String = ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance))
      val eventsProtos: Seq[String] =
        Seq(ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))

      val handlerSetting: ChiefOfStateHandlerSetting = ChiefOfStateHandlerSetting(stateProto, eventsProtos)

      val event = AccountOpened()
        .withAccountNumber(accountNumber)
        .withAccountUuid(accouuntId)

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[HandlerServiceClient]

      (mockGrpcClient
        .handleReadSide(_: HandleReadSideRequest))
        .expects(
          HandleReadSideRequest()
            .withEvent(Any.pack(event))
            .withState(state.getCurrentState)
            .withMeta(Any.pack(eventMeta))
        )
        .returning(Future.failed(new GrpcServiceException(Status.NOT_FOUND)))

      val readSideProcessor = new ChiefOfStateReadProcessor(null, mockGrpcClient, handlerSetting, null)
      an[NamelyException] shouldBe thrownBy(
        readSideProcessor.handle(Event().withEvent(Any.pack(event)), NamelyState(state, eventMeta))
      )
    }

    "handle grpc exception sent by read processor as expected" in {
      val state: State = State.defaultInstance
      val eventMeta: EventMeta = EventMeta.defaultInstance
      val accouuntId: String = UUID.randomUUID.toString
      val accountNumber: String = "123445"

      val stateProto: String = ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance))
      val eventsProtos: Seq[String] =
        Seq(ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))

      val handlerSetting: ChiefOfStateHandlerSetting = ChiefOfStateHandlerSetting(stateProto, eventsProtos)

      val event = AccountOpened()
        .withAccountNumber(accountNumber)
        .withAccountUuid(accouuntId)

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[HandlerServiceClient]

      (mockGrpcClient
        .handleReadSide(_: HandleReadSideRequest))
        .expects(
          HandleReadSideRequest()
            .withEvent(Any.pack(event))
            .withState(state.getCurrentState)
            .withMeta(Any.pack(eventMeta))
        )
        .throws(new GrpcServiceException(Status.INVALID_ARGUMENT))

      val readSideProcessor = new ChiefOfStateReadProcessor(null, mockGrpcClient, handlerSetting, null)
      an[NamelyException] shouldBe thrownBy(
        readSideProcessor.handle(Event().withEvent(Any.pack(event)), NamelyState(state, eventMeta))
      )
    }

    "handle unknown event" in {
      val state: State = State.defaultInstance
      val eventMeta: EventMeta = EventMeta.defaultInstance
      val accouuntId: String = UUID.randomUUID.toString
      val accountNumber: String = "123445"

      val stateProto: String = ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance))
      val eventsProtos: Seq[String] =
        Seq(ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))

      val handlerSetting: ChiefOfStateHandlerSetting = ChiefOfStateHandlerSetting(stateProto, eventsProtos)

      val event = AccountOpened()
        .withAccountNumber(accountNumber)
        .withAccountUuid(accouuntId)

      val readSideProcessor = new ChiefOfStateReadProcessor(null, null, handlerSetting, null)
      an[NamelyException] shouldBe thrownBy(readSideProcessor.handle(Any.pack(event), NamelyState(state, eventMeta)))
    }
  }
}
