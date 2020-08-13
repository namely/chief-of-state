package com.namely.chiefofstate

import java.util.UUID

import akka.grpc.GrpcServiceException
import com.google.protobuf.any.Any
import com.namely.protobuf.chief_of_state.common
import com.namely.protobuf.chief_of_state.persistence.{Event, State}
import com.namely.protobuf.chief_of_state.tests.{Account, AccountOpened}
import com.namely.protobuf.chief_of_state.writeside.{HandleEventRequest, HandleEventResponse, WriteSideHandlerServiceClient}
import com.namely.chiefofstate.config.HandlerSetting
import io.grpc.Status
import io.superflat.lagompb.GlobalException
import io.superflat.lagompb.protobuf.core.MetaData
import io.superflat.lagompb.testkit.BaseSpec
import org.scalamock.scalatest.MockFactory

import scala.concurrent.Future

class AggregateEventHandlerSpec extends BaseSpec with MockFactory {

  "Chief-Of-State Event Handler" should {

    "handle event successfully as expected" in {
      val priorState: State = State.defaultInstance
      val eventMeta: MetaData = MetaData.defaultInstance
      val accountId: String = UUID.randomUUID.toString
      val accountNumber: String = "123445"

      val stateProto: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance)))
      val eventsProtos: Seq[String] =
        Seq(Util.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))

      val handlerSetting: HandlerSetting = HandlerSetting(enableProtoValidations = true, stateProto, eventsProtos)

      val event = AccountOpened()
        .withAccountNumber(accountNumber)
        .withAccountUuid(accountId)

      val resultingState = Account()
        .withAccountNumber(accountNumber)
        .withAccountUuid(accountId)
        .withBalance(100)

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[WriteSideHandlerServiceClient]

      (mockGrpcClient
        .handleEvent(_: HandleEventRequest))
        .expects(
          HandleEventRequest()
            .withEvent(Any.pack(event))
            .withCurrentState(priorState.getCurrentState)
            .withMeta(
              common
                .MetaData()
                .withData(eventMeta.data)
                .withRevisionDate(eventMeta.getRevisionDate)
                .withRevisionNumber(eventMeta.revisionNumber)
            )
        )
        .returning(
          Future.successful(
            HandleEventResponse()
              .withResultingState(Any.pack(resultingState))
          )
        )

      val eventHandler: AggregateEventHandler = new AggregateEventHandler(null, mockGrpcClient, handlerSetting)
      val result: State = eventHandler.handle(Event().withEvent(Any.pack(event)), priorState, eventMeta)
      result shouldBe (State().withCurrentState(Any.pack(resultingState)))
      result.getCurrentState.unpack[Account] shouldBe (resultingState)
    }

    "handle event when event type is not specified in handler settings as expected" in {
      val priorState: State = State.defaultInstance
      val eventMeta: MetaData = MetaData.defaultInstance
      val accouuntId: String = UUID.randomUUID.toString
      val accountNumber: String = "123445"

      val stateProto: Seq[String] = Seq("namely.rogue.state")
      val eventsProtos: Seq[String] =
        Seq(Util.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))

      val handlerSetting: HandlerSetting = HandlerSetting(enableProtoValidations = true, stateProto, eventsProtos)

      val event = AccountOpened()
        .withAccountNumber(accountNumber)
        .withAccountUuid(accouuntId)

      val resultingState = Account()
        .withAccountNumber(accountNumber)
        .withAccountUuid(accouuntId)
        .withBalance(100)

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[WriteSideHandlerServiceClient]

      (mockGrpcClient
        .handleEvent(_: HandleEventRequest))
        .expects(
          HandleEventRequest()
            .withEvent(Any.pack(event))
            .withCurrentState(priorState.getCurrentState)
            .withMeta(
              common
                .MetaData()
                .withData(eventMeta.data)
                .withRevisionDate(eventMeta.getRevisionDate)
                .withRevisionNumber(eventMeta.revisionNumber)
            )
        )
        .returning(
          Future.successful(
            HandleEventResponse()
              .withResultingState(Any.pack(resultingState))
          )
        )

      val eventHandler: AggregateEventHandler = new AggregateEventHandler(null, mockGrpcClient, handlerSetting)
      a[GlobalException] shouldBe thrownBy(
        eventHandler.handle(Event().withEvent(Any.pack(event)), priorState, eventMeta)
      )
    }

    "handle event when event protos validation is disabled in handler settings as expected" in {
      val priorState: State = State.defaultInstance
      val eventMeta: MetaData = MetaData.defaultInstance
      val accouuntId: String = UUID.randomUUID.toString
      val accountNumber: String = "123445"

      val handlerSetting: HandlerSetting = HandlerSetting(enableProtoValidations = false, Seq.empty, Seq.empty)

      val event = AccountOpened()
        .withAccountNumber(accountNumber)
        .withAccountUuid(accouuntId)

      val resultingState = Account()
        .withAccountNumber(accountNumber)
        .withAccountUuid(accouuntId)
        .withBalance(100)

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[WriteSideHandlerServiceClient]

      (mockGrpcClient
        .handleEvent(_: HandleEventRequest))
        .expects(
          HandleEventRequest()
            .withEvent(Any.pack(event))
            .withCurrentState(priorState.getCurrentState)
            .withMeta(
              common
                .MetaData()
                .withData(eventMeta.data)
                .withRevisionDate(eventMeta.getRevisionDate)
                .withRevisionNumber(eventMeta.revisionNumber)
            )
        )
        .returning(
          Future.successful(
            HandleEventResponse()
              .withResultingState(Any.pack(resultingState))
          )
        )

      val eventHandler: AggregateEventHandler = new AggregateEventHandler(null, mockGrpcClient, handlerSetting)

      noException shouldBe thrownBy(eventHandler.handle(Event().withEvent(Any.pack(event)), priorState, eventMeta))
    }

    "handle event when event validation is enabled and the FQNs not provided in handler settings as expected" in {
      val priorState: State = State.defaultInstance
      val eventMeta: MetaData = MetaData.defaultInstance
      val accouuntId: String = UUID.randomUUID.toString
      val accountNumber: String = "123445"

      val handlerSetting: HandlerSetting = HandlerSetting(enableProtoValidations = true, Seq.empty, Seq.empty)

      val event = AccountOpened()
        .withAccountNumber(accountNumber)
        .withAccountUuid(accouuntId)

      val resultingState = Account()
        .withAccountNumber(accountNumber)
        .withAccountUuid(accouuntId)
        .withBalance(100)

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[WriteSideHandlerServiceClient]

      (mockGrpcClient
        .handleEvent(_: HandleEventRequest))
        .expects(
          HandleEventRequest()
            .withEvent(Any.pack(event))
            .withCurrentState(priorState.getCurrentState)
            .withMeta(
              common
                .MetaData()
                .withData(eventMeta.data)
                .withRevisionDate(eventMeta.getRevisionDate)
                .withRevisionNumber(eventMeta.revisionNumber)
            )
        )
        .returning(
          Future.successful(
            HandleEventResponse()
              .withResultingState(Any.pack(resultingState))
          )
        )

      val eventHandler: AggregateEventHandler = new AggregateEventHandler(null, mockGrpcClient, handlerSetting)

      a[GlobalException] shouldBe thrownBy(
        eventHandler.handle(Event().withEvent(Any.pack(event)), priorState, eventMeta)
      )
    }

    "handle failed response as expected" in {
      val priorState: State = State.defaultInstance
      val eventMeta: MetaData = MetaData.defaultInstance
      val accouuntId: String = UUID.randomUUID.toString
      val accountNumber: String = "123445"

      val stateProto: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance)))
      val eventsProtos: Seq[String] =
        Seq(Util.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))

      val handlerSetting: HandlerSetting = HandlerSetting(enableProtoValidations = true, stateProto, eventsProtos)

      val event = AccountOpened()
        .withAccountNumber(accountNumber)
        .withAccountUuid(accouuntId)

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[WriteSideHandlerServiceClient]

      (mockGrpcClient
        .handleEvent(_: HandleEventRequest))
        .expects(
          HandleEventRequest()
            .withEvent(Any.pack(event))
            .withCurrentState(priorState.getCurrentState)
            .withMeta(
              common
                .MetaData()
                .withData(eventMeta.data)
                .withRevisionDate(eventMeta.getRevisionDate)
                .withRevisionNumber(eventMeta.revisionNumber)
            )
        )
        .returning(Future.failed(new GrpcServiceException(Status.INTERNAL)))

      val eventHandler: AggregateEventHandler = new AggregateEventHandler(null, mockGrpcClient, handlerSetting)
      a[GlobalException] shouldBe thrownBy(
        eventHandler.handle(Event().withEvent(Any.pack(event)), priorState, eventMeta)
      )
    }

    "handle broken event handler as expected" in {
      val priorState: State = State.defaultInstance
      val eventMeta: MetaData = MetaData.defaultInstance
      val accouuntId: String = UUID.randomUUID.toString
      val accountNumber: String = "123445"

      val stateProto: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance)))
      val eventsProtos: Seq[String] =
        Seq(Util.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))

      val handlerSetting: HandlerSetting = HandlerSetting(enableProtoValidations = true, stateProto, eventsProtos)

      val event = AccountOpened()
        .withAccountNumber(accountNumber)
        .withAccountUuid(accouuntId)

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[WriteSideHandlerServiceClient]

      (mockGrpcClient
        .handleEvent(_: HandleEventRequest))
        .expects(
          HandleEventRequest()
            .withEvent(Any.pack(event))
            .withCurrentState(priorState.getCurrentState)
            .withMeta(
              common
                .MetaData()
                .withData(eventMeta.data)
                .withRevisionDate(eventMeta.getRevisionDate)
                .withRevisionNumber(eventMeta.revisionNumber)
            )
        )
        .throws(new RuntimeException("broken"))

      val eventHandler: AggregateEventHandler = new AggregateEventHandler(null, mockGrpcClient, handlerSetting)
      a[GlobalException] shouldBe thrownBy(
        eventHandler.handle(Event().withEvent(Any.pack(event)), priorState, eventMeta)
      )
    }
  }
}
