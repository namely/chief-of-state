package com.namely.chiefofstate

import java.util.UUID

import akka.Done
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.grpc.scaladsl.SingleResponseRequestBuilder
import akka.grpc.GrpcServiceException
import com.google.protobuf.any.Any
import com.namely.protobuf.chief_of_state.common
import com.namely.protobuf.chief_of_state.persistence.{Event, State}
import com.namely.protobuf.chief_of_state.readside.{
  HandleReadSideRequest,
  HandleReadSideResponse,
  ReadSideHandlerServiceClient
}
import com.namely.protobuf.chief_of_state.tests.{Account, AccountOpened}
import com.namely.chiefofstate.config.{
  HandlerSetting,
  ReadSideConfig
}
import io.grpc.Status
import io.superflat.lagompb.protobuf.core.MetaData
import io.superflat.lagompb.testkit.BaseActorTestKit
import io.superflat.lagompb.GlobalException
import io.superflat.lagompb.encryption.{EncryptionAdapter, NoEncryption}
import io.superflat.lagompb.readside.ReadSideEvent
import org.scalamock.scalatest.MockFactory
import slick.dbio.DBIO

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ReadSideHandlerSpec
    extends BaseActorTestKit(s"""
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
  val defaultGrpcReadSideConfig: ReadSideConfig = ReadSideConfig("test")

  "Chief-Of-State ReadSide Processor" should {

    val encryptionAdapter: EncryptionAdapter = new EncryptionAdapter(Some(NoEncryption))

    "handle events and state as expected when response was successful" in {
      val state: State = State.defaultInstance
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

      val readSideEvent = ReadSideEvent(
        event = event,
        eventTag = "tag1",
        state = state,
        metaData = eventMeta
      )

      val requestBuilder: SingleResponseRequestBuilder[HandleReadSideRequest, HandleReadSideResponse] =
        mock[SingleResponseRequestBuilder[HandleReadSideRequest, HandleReadSideResponse]]

      (requestBuilder
        .addHeader(_: String, _: String))
        .expects("x-cos-event-tag", readSideEvent.eventTag)
        .returning(requestBuilder)

      (requestBuilder
        .addHeader(_: String, _: String))
        .expects("x-cos-entity-id", eventMeta.entityId)
        .returning(requestBuilder)

      (requestBuilder.invoke _)
        .expects(
          HandleReadSideRequest()
            .withEvent(Any.pack(event))
            .withState(state.getCurrentState)
            .withMeta(
              common
                .MetaData()
                .withEntityId(eventMeta.entityId)
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

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[ReadSideHandlerServiceClient]

      (mockGrpcClient.handleReadSide _: () => SingleResponseRequestBuilder[HandleReadSideRequest,
                                                                           HandleReadSideResponse
      ])
        .expects()
        .returning(requestBuilder)

      val readSideProcessor =
        new ReadSideHandler(
          defaultGrpcReadSideConfig,
          encryptionAdapter,
          testKit.system.toClassic,
          mockGrpcClient,
          handlerSetting
        )

      val result: DBIO[Done] =
        readSideProcessor.handle(
          ReadSideEvent(
            event = Event()
              .withEvent(Any.pack(event)),
            eventTag = readSideEvent.eventTag,
            state = state,
            metaData = eventMeta
          )
        )

      result.map(r => r shouldBe (Done))
    }

    "handle events and state as expected when response was not successful" in {
      val state: State = State.defaultInstance
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

      val readSideEvent = ReadSideEvent(
        event = event,
        eventTag = "tag1",
        state = state,
        metaData = eventMeta
      )

      val requestBuilder: SingleResponseRequestBuilder[HandleReadSideRequest, HandleReadSideResponse] =
        mock[SingleResponseRequestBuilder[HandleReadSideRequest, HandleReadSideResponse]]

      (requestBuilder
        .addHeader(_: String, _: String))
        .expects("x-cos-event-tag", readSideEvent.eventTag)
        .returning(requestBuilder)

      (requestBuilder
        .addHeader(_: String, _: String))
        .expects("x-cos-entity-id", eventMeta.entityId)
        .returning(requestBuilder)

      (requestBuilder.invoke _)
        .expects(
          HandleReadSideRequest()
            .withEvent(Any.pack(event))
            .withState(state.getCurrentState)
            .withMeta(
              common
                .MetaData()
                .withEntityId(eventMeta.entityId)
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

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[ReadSideHandlerServiceClient]

      (mockGrpcClient.handleReadSide _: () => SingleResponseRequestBuilder[HandleReadSideRequest,
                                                                           HandleReadSideResponse
      ])
        .expects()
        .returning(requestBuilder)

      val readSideProcessor =
        new ReadSideHandler(
          defaultGrpcReadSideConfig,
          encryptionAdapter,
          testKit.system.toClassic,
          mockGrpcClient,
          handlerSetting
        )
      an[GlobalException] shouldBe thrownBy(
        readSideProcessor.handle(
          ReadSideEvent(
            event = Event()
              .withEvent(Any.pack(event)),
            eventTag = readSideEvent.eventTag,
            state = state,
            metaData = eventMeta
          )
        )
      )
    }

    "handle events and state as expected when handler failed" in {
      val state: State = State.defaultInstance
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

      val readSideEvent = ReadSideEvent(
        event = event,
        eventTag = "tag1",
        state = state,
        metaData = eventMeta
      )

      val requestBuilder: SingleResponseRequestBuilder[HandleReadSideRequest, HandleReadSideResponse] =
        mock[SingleResponseRequestBuilder[HandleReadSideRequest, HandleReadSideResponse]]

      (requestBuilder
        .addHeader(_: String, _: String))
        .expects("x-cos-event-tag", readSideEvent.eventTag)
        .returning(requestBuilder)

      (requestBuilder
        .addHeader(_: String, _: String))
        .expects("x-cos-entity-id", eventMeta.entityId)
        .returning(requestBuilder)

      (requestBuilder.invoke _)
        .expects(
          HandleReadSideRequest()
            .withEvent(Any.pack(event))
            .withState(state.getCurrentState)
            .withMeta(
              common
                .MetaData()
                .withEntityId(eventMeta.entityId)
                .withData(eventMeta.data)
                .withRevisionDate(eventMeta.getRevisionDate)
                .withRevisionNumber(eventMeta.revisionNumber)
            )
        )
        .throws(new RuntimeException("broken"))

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[ReadSideHandlerServiceClient]

      (mockGrpcClient.handleReadSide _: () => SingleResponseRequestBuilder[HandleReadSideRequest,
                                                                           HandleReadSideResponse
      ])
        .expects()
        .returning(requestBuilder)

      val readSideProcessor =
        new ReadSideHandler(
          defaultGrpcReadSideConfig,
          encryptionAdapter,
          testKit.system.toClassic,
          mockGrpcClient,
          handlerSetting
        )
      an[GlobalException] shouldBe thrownBy(
        readSideProcessor.handle(
          ReadSideEvent(
            event = Event()
              .withEvent(Any.pack(event)),
            eventTag = readSideEvent.eventTag,
            state = state,
            metaData = eventMeta
          )
        )
      )
    }

    "handle failed response as expected" in {
      val state: State = State.defaultInstance
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

      val readSideEvent = ReadSideEvent(
        event = event,
        eventTag = "tag1",
        state = state,
        metaData = eventMeta
      )

      val requestBuilder: SingleResponseRequestBuilder[HandleReadSideRequest, HandleReadSideResponse] =
        mock[SingleResponseRequestBuilder[HandleReadSideRequest, HandleReadSideResponse]]

      (requestBuilder
        .addHeader(_: String, _: String))
        .expects("x-cos-event-tag", readSideEvent.eventTag)
        .returning(requestBuilder)

      (requestBuilder
        .addHeader(_: String, _: String))
        .expects("x-cos-entity-id", eventMeta.entityId)
        .returning(requestBuilder)

      (requestBuilder.invoke _)
        .expects(
          HandleReadSideRequest()
            .withEvent(Any.pack(event))
            .withState(state.getCurrentState)
            .withMeta(
              common
                .MetaData()
                .withEntityId(eventMeta.entityId)
                .withData(eventMeta.data)
                .withRevisionDate(eventMeta.getRevisionDate)
                .withRevisionNumber(eventMeta.revisionNumber)
            )
        )
        .returning(
          Future.failed(
            new GrpcServiceException(Status.NOT_FOUND)
          )
        )

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[ReadSideHandlerServiceClient]

      (mockGrpcClient.handleReadSide _: () => SingleResponseRequestBuilder[HandleReadSideRequest,
                                                                           HandleReadSideResponse
      ])
        .expects()
        .returning(requestBuilder)

      val readSideProcessor =
        new ReadSideHandler(
          defaultGrpcReadSideConfig,
          encryptionAdapter,
          testKit.system.toClassic,
          mockGrpcClient,
          handlerSetting
        )
      an[GlobalException] shouldBe thrownBy(
        readSideProcessor.handle(
          ReadSideEvent(
            event = Event()
              .withEvent(Any.pack(event)),
            eventTag = readSideEvent.eventTag,
            state = state,
            metaData = eventMeta
          )
        )
      )
    }

    "handle grpc exception sent by read processor as expected" in {
      val state: State = State.defaultInstance
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

      val readSideEvent = ReadSideEvent(
        event = event,
        eventTag = "tag1",
        state = state,
        metaData = eventMeta
      )

      val requestBuilder: SingleResponseRequestBuilder[HandleReadSideRequest, HandleReadSideResponse] =
        mock[SingleResponseRequestBuilder[HandleReadSideRequest, HandleReadSideResponse]]

      (requestBuilder
        .addHeader(_: String, _: String))
        .expects("x-cos-event-tag", readSideEvent.eventTag)
        .returning(requestBuilder)

      (requestBuilder
        .addHeader(_: String, _: String))
        .expects("x-cos-entity-id", eventMeta.entityId)
        .returning(requestBuilder)

      (requestBuilder.invoke _)
        .expects(
          HandleReadSideRequest()
            .withEvent(Any.pack(event))
            .withState(state.getCurrentState)
            .withMeta(
              common
                .MetaData()
                .withEntityId(eventMeta.entityId)
                .withData(eventMeta.data)
                .withRevisionDate(eventMeta.getRevisionDate)
                .withRevisionNumber(eventMeta.revisionNumber)
            )
        )
        .returning(
          Future.failed(
            new GrpcServiceException(Status.INVALID_ARGUMENT)
          )
        )

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[ReadSideHandlerServiceClient]

      (mockGrpcClient.handleReadSide _: () => SingleResponseRequestBuilder[HandleReadSideRequest,
                                                                           HandleReadSideResponse
      ])
        .expects()
        .returning(requestBuilder)

      val readSideProcessor =
        new ReadSideHandler(
          defaultGrpcReadSideConfig,
          encryptionAdapter,
          testKit.system.toClassic,
          mockGrpcClient,
          handlerSetting
        )
      an[GlobalException] shouldBe thrownBy(
        readSideProcessor.handle(
          ReadSideEvent(
            event = Event()
              .withEvent(Any.pack(event)),
            eventTag = readSideEvent.eventTag,
            state = state,
            metaData = eventMeta
          )
        )
      )
    }

    "handle unknown event" in {
      val state: State = State.defaultInstance
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

      val readSideProcessor =
        new ReadSideHandler(
          defaultGrpcReadSideConfig,
          encryptionAdapter,
          testKit.system.toClassic,
          null,
          handlerSetting
        )
      an[GlobalException] shouldBe thrownBy(
        readSideProcessor.handle(
          ReadSideEvent(
            event = Event()
              .withEvent(Any.pack(event)),
            eventTag = "",
            state = state,
            metaData = eventMeta
          )
        )
      )
    }

  }

}
