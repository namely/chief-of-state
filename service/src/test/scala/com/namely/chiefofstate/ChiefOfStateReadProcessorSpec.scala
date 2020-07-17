package com.namely.chiefofstate

import java.util.UUID

import akka.Done
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.grpc.GrpcServiceException
import com.google.protobuf.any.Any
import com.namely.protobuf.chief_of_state.cos_common
import com.namely.protobuf.chief_of_state.cos_persistence.{Event, State}
import com.namely.protobuf.chief_of_state.cos_readside_handler.{
  HandleReadSideRequest,
  HandleReadSideResponse,
  ReadSideHandlerServiceClient
}
import com.namely.protobuf.chief_of_state.tests.{Account, AccountOpened}
import io.grpc.Status
import io.superflat.lagompb.protobuf.core.MetaData
import io.superflat.lagompb.testkit.LagompbActorTestKit
import io.superflat.lagompb.GlobalException
import io.superflat.lagompb.encryption.NoEncryption
import org.scalamock.scalatest.MockFactory
import slick.dbio.DBIO

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ChiefOfStateReadProcessorSpec
    extends LagompbActorTestKit(s"""
    akka {
      actor {
        serialize-messages = on
        serializers {
          proto = "akka.remote.serialization.ProtobufSerializer"
         cmdSerializer = "io.superflat.lagompb.CommandSerializer"
        }
        serialization-bindings {
          "scalapb.GeneratedMessage" = proto
         "io.superflat.lagompb.Command" = cmdSerializer
        }
      }
    }
    """)
    with MockFactory {

  val sys: ActorSystem[Nothing] = testKit.system

  "Chief-Of-State ReadSide Processor" should {

    "handle events and state as expected when response was successful" in {
      val state: State = State.defaultInstance
      val eventMeta: MetaData = MetaData.defaultInstance
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
      val mockGrpcClient = mock[ReadSideHandlerServiceClient]

      (mockGrpcClient
        .handleReadSide(_: HandleReadSideRequest))
        .expects(
          HandleReadSideRequest()
            .withEvent(Any.pack(event))
            .withState(state.getCurrentState)
            .withMeta(
              cos_common
                .MetaData()
                .withData(eventMeta.data)
                .withRevisionDate(eventMeta.getRevisionDate)
                .withRevisionNumber(eventMeta.revisionNumber)
            )
        )
        .returning(
          Future.successful(
            HandleReadSideResponse()
              .withSuccessful(true)
          )
        )

      val readSideProcessor =
        new ChiefOfStateReadProcessor("test", NoEncryption, testKit.system.toClassic, mockGrpcClient, handlerSetting)
      val result: DBIO[Done] =
        readSideProcessor.handle(Event().withEvent(Any.pack(event)), state, eventMeta)
      result.map(r => r shouldBe (Done))
    }

    "handle events and state as expected when response was not successful" in {
      val state: State = State.defaultInstance
      val eventMeta: MetaData = MetaData.defaultInstance
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
      val mockGrpcClient = mock[ReadSideHandlerServiceClient]

      (mockGrpcClient
        .handleReadSide(_: HandleReadSideRequest))
        .expects(
          HandleReadSideRequest()
            .withEvent(Any.pack(event))
            .withState(state.getCurrentState)
            .withMeta(
              cos_common
                .MetaData()
                .withData(eventMeta.data)
                .withRevisionDate(eventMeta.getRevisionDate)
                .withRevisionNumber(eventMeta.revisionNumber)
            )
        )
        .returning(
          Future.successful(
            HandleReadSideResponse()
              .withSuccessful(false)
          )
        )

      val readSideProcessor =
        new ChiefOfStateReadProcessor("test", NoEncryption, testKit.system.toClassic, mockGrpcClient, handlerSetting)
      an[GlobalException] shouldBe thrownBy(
        readSideProcessor.handle(Event().withEvent(Any.pack(event)), state, eventMeta)
      )
    }

    "handle events and state as expected when handler failed" in {
      val state: State = State.defaultInstance
      val eventMeta: MetaData = MetaData.defaultInstance
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
      val mockGrpcClient = mock[ReadSideHandlerServiceClient]

      (mockGrpcClient
        .handleReadSide(_: HandleReadSideRequest))
        .expects(
          HandleReadSideRequest()
            .withEvent(Any.pack(event))
            .withState(state.getCurrentState)
            .withMeta(
              cos_common
                .MetaData()
                .withData(eventMeta.data)
                .withRevisionDate(eventMeta.getRevisionDate)
                .withRevisionNumber(eventMeta.revisionNumber)
            )
        )
        .throws(new RuntimeException("broken"))

      val readSideProcessor =
        new ChiefOfStateReadProcessor("test", NoEncryption, testKit.system.toClassic, mockGrpcClient, handlerSetting)
      an[GlobalException] shouldBe thrownBy(
        readSideProcessor.handle(Event().withEvent(Any.pack(event)), state, eventMeta)
      )
    }

    "handle failed response as expected" in {
      val state: State = State.defaultInstance
      val eventMeta: MetaData = MetaData.defaultInstance
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
      val mockGrpcClient = mock[ReadSideHandlerServiceClient]

      (mockGrpcClient
        .handleReadSide(_: HandleReadSideRequest))
        .expects(
          HandleReadSideRequest()
            .withEvent(Any.pack(event))
            .withState(state.getCurrentState)
            .withMeta(
              cos_common
                .MetaData()
                .withData(eventMeta.data)
                .withRevisionDate(eventMeta.getRevisionDate)
                .withRevisionNumber(eventMeta.revisionNumber)
            )
        )
        .returning(Future.failed(new GrpcServiceException(Status.NOT_FOUND)))

      val readSideProcessor =
        new ChiefOfStateReadProcessor("test", NoEncryption, testKit.system.toClassic, mockGrpcClient, handlerSetting)
      an[GlobalException] shouldBe thrownBy(
        readSideProcessor.handle(Event().withEvent(Any.pack(event)), state, eventMeta)
      )
    }

    "handle grpc exception sent by read processor as expected" in {
      val state: State = State.defaultInstance
      val eventMeta: MetaData = MetaData.defaultInstance
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
      val mockGrpcClient = mock[ReadSideHandlerServiceClient]

      (mockGrpcClient
        .handleReadSide(_: HandleReadSideRequest))
        .expects(
          HandleReadSideRequest()
            .withEvent(Any.pack(event))
            .withState(state.getCurrentState)
            .withMeta(
              cos_common
                .MetaData()
                .withData(eventMeta.data)
                .withRevisionDate(eventMeta.getRevisionDate)
                .withRevisionNumber(eventMeta.revisionNumber)
            )
        )
        .throws(new GrpcServiceException(Status.INVALID_ARGUMENT))

      val readSideProcessor =
        new ChiefOfStateReadProcessor("test", NoEncryption, testKit.system.toClassic, mockGrpcClient, handlerSetting)
      an[GlobalException] shouldBe thrownBy(
        readSideProcessor.handle(Event().withEvent(Any.pack(event)), state, eventMeta)
      )
    }

    "handle unknown event" in {
      val state: State = State.defaultInstance
      val eventMeta: MetaData = MetaData.defaultInstance
      val accouuntId: String = UUID.randomUUID.toString
      val accountNumber: String = "123445"

      val stateProto: String = ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance))
      val eventsProtos: Seq[String] =
        Seq(ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))

      val handlerSetting: ChiefOfStateHandlerSetting = ChiefOfStateHandlerSetting(stateProto, eventsProtos)

      val event = AccountOpened()
        .withAccountNumber(accountNumber)
        .withAccountUuid(accouuntId)

      val readSideProcessor =
        new ChiefOfStateReadProcessor("test", NoEncryption, testKit.system.toClassic, null, handlerSetting)
      an[GlobalException] shouldBe thrownBy(readSideProcessor.handle(Any.pack(event), state, eventMeta))
    }
  }
}
