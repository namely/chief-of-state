package com.namely.chiefofstate

import java.util.UUID

import akka.grpc.GrpcServiceException
import com.google.protobuf.any.Any
import com.namely.lagom.NamelyException
import com.namely.lagom.testkit.NamelyTestSpec
import com.namely.protobuf.chief_of_state.handler.{HandleEventRequest, HandleEventResponse, HandlerServiceClient}
import com.namely.protobuf.chief_of_state.persistence.{Event, State}
import com.namely.protobuf.chief_of_state.tests.{Account, AccountOpened}
import com.namely.protobuf.lagom.common.EventMeta
import io.grpc.Status
import org.scalamock.scalatest.MockFactory

import scala.concurrent.Future

class ChiefOfStateEventHandlerSpec extends NamelyTestSpec with MockFactory {

  "Chief-Of-State Event Handler" should {

    "handle event successfully as expected" in {
      val priorState: State = State.defaultInstance
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

      val resultingState = Account()
        .withAccountNumber(accountNumber)
        .withAccountUuid(accouuntId)
        .withBalance(100)

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[HandlerServiceClient]

      (mockGrpcClient
        .handleEvent(_: HandleEventRequest))
        .expects(
          HandleEventRequest()
            .withEvent(Any.pack(event))
            .withCurrentState(priorState.getCurrentState)
            .withMeta(Any.pack(eventMeta))
        )
        .returning(
          Future.successful(
            HandleEventResponse()
              .withResultingState(Any.pack(resultingState))
          )
        )

      val eventHandler: ChiefOfStateEventHandler = new ChiefOfStateEventHandler(null, mockGrpcClient, handlerSetting)
      val result: State = eventHandler.handle(Event().withEvent(Any.pack(event)), priorState, eventMeta)
      result shouldBe (State().withCurrentState(Any.pack(resultingState)))
      result.getCurrentState.unpack[Account] shouldBe (resultingState)
    }

    "handle event when event type is not specified in handler settings as expected" in {
      val priorState: State = State.defaultInstance
      val eventMeta: EventMeta = EventMeta.defaultInstance
      val accouuntId: String = UUID.randomUUID.toString
      val accountNumber: String = "123445"

      val stateProto: String = "namely.rogue.state"
      val eventsProtos: Seq[String] =
        Seq(ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))

      val handlerSetting: ChiefOfStateHandlerSetting = ChiefOfStateHandlerSetting(stateProto, eventsProtos)

      val event = AccountOpened()
        .withAccountNumber(accountNumber)
        .withAccountUuid(accouuntId)

      val resultingState = Account()
        .withAccountNumber(accountNumber)
        .withAccountUuid(accouuntId)
        .withBalance(100)

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[HandlerServiceClient]

      (mockGrpcClient
        .handleEvent(_: HandleEventRequest))
        .expects(
          HandleEventRequest()
            .withEvent(Any.pack(event))
            .withCurrentState(priorState.getCurrentState)
            .withMeta(Any.pack(eventMeta))
        )
        .returning(
          Future.successful(
            HandleEventResponse()
              .withResultingState(Any.pack(resultingState))
          )
        )

      val eventHandler: ChiefOfStateEventHandler = new ChiefOfStateEventHandler(null, mockGrpcClient, handlerSetting)
      a[NamelyException] shouldBe thrownBy(
        eventHandler.handle(Event().withEvent(Any.pack(event)), priorState, eventMeta)
      )
    }

    "handle failed response as expected" in {
      val priorState: State = State.defaultInstance
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
        .handleEvent(_: HandleEventRequest))
        .expects(
          HandleEventRequest()
            .withEvent(Any.pack(event))
            .withCurrentState(priorState.getCurrentState)
            .withMeta(Any.pack(eventMeta))
        )
        .returning(Future.failed(new GrpcServiceException(Status.INTERNAL)))

      val eventHandler: ChiefOfStateEventHandler = new ChiefOfStateEventHandler(null, mockGrpcClient, handlerSetting)
      a[NamelyException] shouldBe thrownBy(
        eventHandler.handle(Event().withEvent(Any.pack(event)), priorState, eventMeta)
      )
    }

    "handle broken event handler as expected" in {
      val priorState: State = State.defaultInstance
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
        .handleEvent(_: HandleEventRequest))
        .expects(
          HandleEventRequest()
            .withEvent(Any.pack(event))
            .withCurrentState(priorState.getCurrentState)
            .withMeta(Any.pack(eventMeta))
        )
        .throws(new RuntimeException("broken"))

      val eventHandler: ChiefOfStateEventHandler = new ChiefOfStateEventHandler(null, mockGrpcClient, handlerSetting)
      a[NamelyException] shouldBe thrownBy(
        eventHandler.handle(Event().withEvent(Any.pack(event)), priorState, eventMeta)
      )
    }
  }
}
