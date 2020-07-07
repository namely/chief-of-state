package com.namely.chiefofstate

import java.util.UUID

import akka.grpc.GrpcServiceException
import com.google.protobuf.any.Any
import com.namely.protobuf.chief_of_state.cos_common.{MetaData => _}
import com.namely.protobuf.chief_of_state.cos_common
import com.namely.protobuf.chief_of_state.cos_persistence.{Event, State}
import com.namely.protobuf.chief_of_state.cos_writeside_handler._
import com.namely.protobuf.chief_of_state.cos_writeside_handler.HandleCommandResponse.ResponseType
import com.namely.protobuf.chief_of_state.tests.{Account, AccountOpened, OpenAccount}
import io.grpc.Status
import io.superflat.lagompb.protobuf.core._
import io.superflat.lagompb.testkit.LagompbSpec
import io.superflat.lagompb.Command
import org.scalamock.scalatest.MockFactory

import scala.concurrent.Future
import scala.util.{Success, Try}

class ChiefOfStateCommandHandlerSpec extends LagompbSpec with MockFactory {

  "Chief-Of-State Command Handler" should {

    "handle command successfully as expected with an event to persist" in {
      val priorState: State = State.defaultInstance
      val priorEventMeta: MetaData = MetaData.defaultInstance
      val accouuntId: String = UUID.randomUUID.toString
      val accountNumber: String = "123445"

      val stateProto: String = ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance))
      val eventsProtos: Seq[String] =
        Seq(ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))

      val handlerSetting: ChiefOfStateHandlerSetting = ChiefOfStateHandlerSetting(stateProto, eventsProtos)

      val cmd = Command(
        Any.pack(
          OpenAccount()
            .withAccountNumber(accountNumber)
            .withAccountUuid(accouuntId)
        ),
        null, // ignore the actor ref in this test
        Map.empty
      )

      val event = AccountOpened()
        .withAccountNumber(accountNumber)
        .withAccountUuid(accouuntId)

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[WriteSideHandlerServiceClient]

      (mockGrpcClient
        .handleCommand(_: HandleCommandRequest))
        .expects(
          HandleCommandRequest()
            .withCommand(cmd.command.asInstanceOf[Any])
            .withCurrentState(priorState.getCurrentState)
            .withMeta(
              cos_common
                .MetaData()
                .withData(priorEventMeta.data)
                .withRevisionDate(priorEventMeta.getRevisionDate)
                .withRevisionNumber(priorEventMeta.revisionNumber)
            )
        )
        .returning(
          Future.successful(
            HandleCommandResponse()
              .withPersistAndReply(
                PersistAndReply()
                  .withEvent(Any.pack(event))
              )
          )
        )

      // let us execute the request
      val cmdhandler = new ChiefOfStateCommandHandler(null, mockGrpcClient, handlerSetting)
      val result: Try[CommandHandlerResponse] = cmdhandler.handle(cmd, priorState, priorEventMeta)
      result shouldBe (Success(
        CommandHandlerResponse()
          .withSuccessResponse(SuccessCommandHandlerResponse().withEvent(Any.pack(Event().withEvent(Any.pack(event)))))
      ))
    }

    "handle command when event type is not specified in handler settings as expected" in {
      val priorState: State = State.defaultInstance
      val priorEventMeta: MetaData = MetaData.defaultInstance
      val accouuntId: String = UUID.randomUUID.toString
      val accountNumber: String = "123445"

      val stateProto: String = ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance))
      val eventsProtos: Seq[String] = Seq("namely.com.SomeEvent")

      val handlerSetting: ChiefOfStateHandlerSetting = ChiefOfStateHandlerSetting(stateProto, eventsProtos)

      val cmd = Command(
        Any.pack(
          OpenAccount()
            .withAccountNumber(accountNumber)
            .withAccountUuid(accouuntId)
        ),
        null, // ignore the actor ref in this test
        Map.empty
      )

      val event = AccountOpened()
        .withAccountNumber(accountNumber)
        .withAccountUuid(accouuntId)

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[WriteSideHandlerServiceClient]

      (mockGrpcClient
        .handleCommand(_: HandleCommandRequest))
        .expects(
          HandleCommandRequest()
            .withCommand(cmd.command.asInstanceOf[Any])
            .withCurrentState(priorState.getCurrentState)
            .withMeta(
              cos_common
                .MetaData()
                .withData(priorEventMeta.data)
                .withRevisionDate(priorEventMeta.getRevisionDate)
                .withRevisionNumber(priorEventMeta.revisionNumber)
            )
        )
        .returning(
          Future.successful(
            HandleCommandResponse()
              .withPersistAndReply(
                PersistAndReply()
                  .withEvent(Any.pack(event))
              )
          )
        )

      // let us execute the request
      val cmdhandler = new ChiefOfStateCommandHandler(null, mockGrpcClient, handlerSetting)
      val result: Try[CommandHandlerResponse] = cmdhandler.handle(cmd, priorState, priorEventMeta)
      result shouldBe (Success(
        CommandHandlerResponse()
          .withFailedResponse(
            FailedCommandHandlerResponse()
              .withReason(new GrpcServiceException(Status.INVALID_ARGUMENT).toString)
              .withCause(FailureCause.ValidationError)
          )
      ))

    }

    "handle command successfully as expected with no event to persist" in {
      val priorState: State = State.defaultInstance
      val priorEventMeta: MetaData = MetaData.defaultInstance
      val accouuntId: String = UUID.randomUUID.toString
      val accountNumber: String = "123445"

      val stateProto: String = ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance))
      val eventsProtos: Seq[String] =
        Seq(ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))

      val handlerSetting: ChiefOfStateHandlerSetting = ChiefOfStateHandlerSetting(stateProto, eventsProtos)

      val cmd = Command(
        Any.pack(
          OpenAccount()
            .withAccountNumber(accountNumber)
            .withAccountUuid(accouuntId)
        ),
        null, // ignore the actor ref in this test
        Map.empty
      )

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[WriteSideHandlerServiceClient]

      (mockGrpcClient
        .handleCommand(_: HandleCommandRequest))
        .expects(
          HandleCommandRequest()
            .withCommand(cmd.command.asInstanceOf[Any])
            .withCurrentState(priorState.getCurrentState)
            .withMeta(
              cos_common
                .MetaData()
                .withData(priorEventMeta.data)
                .withRevisionDate(priorEventMeta.getRevisionDate)
                .withRevisionNumber(priorEventMeta.revisionNumber)
            )
        )
        .returning(
          Future.successful(
            HandleCommandResponse()
              .withReply(Reply.defaultInstance)
          )
        )

      // let us execute the request
      val cmdhandler = new ChiefOfStateCommandHandler(null, mockGrpcClient, handlerSetting)
      val result: Try[CommandHandlerResponse] = cmdhandler.handle(cmd, priorState, priorEventMeta)
      result shouldBe (Success(
        CommandHandlerResponse()
          .withSuccessResponse(
            SuccessCommandHandlerResponse()
              .withNoEvent(com.google.protobuf.empty.Empty.defaultInstance)
          )
      ))
    }

    "handle wrong successful response as expected" in {
      val priorState: State = State.defaultInstance
      val priorEventMeta: MetaData = MetaData.defaultInstance
      val accouuntId: String = UUID.randomUUID.toString
      val accountNumber: String = "123445"

      val stateProto: String = ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance))
      val eventsProtos: Seq[String] =
        Seq(ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))

      val handlerSetting: ChiefOfStateHandlerSetting = ChiefOfStateHandlerSetting(stateProto, eventsProtos)

      val cmd = Command(
        Any.pack(
          OpenAccount()
            .withAccountNumber(accountNumber)
            .withAccountUuid(accouuntId)
        ),
        null, // ignore the actor ref in this test
        Map.empty
      )

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[WriteSideHandlerServiceClient]

      (mockGrpcClient
        .handleCommand(_: HandleCommandRequest))
        .expects(
          HandleCommandRequest()
            .withCommand(cmd.command.asInstanceOf[Any])
            .withCurrentState(priorState.getCurrentState)
            .withMeta(
              cos_common
                .MetaData()
                .withData(priorEventMeta.data)
                .withRevisionDate(priorEventMeta.getRevisionDate)
                .withRevisionNumber(priorEventMeta.revisionNumber)
            )
        )
        .returning(
          Future.successful(
            HandleCommandResponse()
              .withResponseType(ResponseType.Empty)
          )
        )

      // let us execute the request
      val cmdhandler = new ChiefOfStateCommandHandler(null, mockGrpcClient, handlerSetting)
      val result: Try[CommandHandlerResponse] = cmdhandler.handle(cmd, priorState, priorEventMeta)
      result shouldBe (Success(
        CommandHandlerResponse()
          .withFailedResponse(
            FailedCommandHandlerResponse()
              .withReason(new GrpcServiceException(Status.INTERNAL).toString)
              .withCause(FailureCause.InternalError)
          )
      ))
    }

    "handle failed response as expected" in {
      val priorState: State = State.defaultInstance
      val priorEventMeta: MetaData = MetaData.defaultInstance
      val accouuntId: String = UUID.randomUUID.toString
      val accountNumber: String = "123445"

      val stateProto: String = ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance))
      val eventsProtos: Seq[String] =
        Seq(ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))

      val handlerSetting: ChiefOfStateHandlerSetting = ChiefOfStateHandlerSetting(stateProto, eventsProtos)

      val cmd = Command(
        Any.pack(
          OpenAccount()
            .withAccountNumber(accountNumber)
            .withAccountUuid(accouuntId)
        ),
        null, // ignore the actor ref in this test
        Map.empty
      )

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[WriteSideHandlerServiceClient]

      (mockGrpcClient
        .handleCommand(_: HandleCommandRequest))
        .expects(
          HandleCommandRequest()
            .withCommand(cmd.command.asInstanceOf[Any])
            .withCurrentState(priorState.getCurrentState)
            .withMeta(
              cos_common
                .MetaData()
                .withData(priorEventMeta.data)
                .withRevisionDate(priorEventMeta.getRevisionDate)
                .withRevisionNumber(priorEventMeta.revisionNumber)
            )
        )
        .returning(Future.failed(new GrpcServiceException(Status.NOT_FOUND)))

      // let us execute the request
      val cmdhandler = new ChiefOfStateCommandHandler(null, mockGrpcClient, handlerSetting)
      val result: Try[CommandHandlerResponse] = cmdhandler.handle(cmd, priorState, priorEventMeta)
      result shouldBe (Success(
        CommandHandlerResponse()
          .withFailedResponse(
            FailedCommandHandlerResponse()
              .withReason(new GrpcServiceException(Status.UNAVAILABLE).toString)
              .withCause(FailureCause.InternalError)
          )
      ))
    }

    "handle grpc exception sent by command handler as expected" in {
      val priorState: State = State.defaultInstance
      val priorEventMeta: MetaData = MetaData.defaultInstance
      val accouuntId: String = UUID.randomUUID.toString
      val accountNumber: String = "123445"

      val stateProto: String = ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance))
      val eventsProtos: Seq[String] =
        Seq(ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))

      val handlerSetting: ChiefOfStateHandlerSetting = ChiefOfStateHandlerSetting(stateProto, eventsProtos)

      val cmd = Command(
        Any.pack(
          OpenAccount()
            .withAccountNumber(accountNumber)
            .withAccountUuid(accouuntId)
        ),
        null, // ignore the actor ref in this test
        Map.empty
      )

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[WriteSideHandlerServiceClient]

      (mockGrpcClient
        .handleCommand(_: HandleCommandRequest))
        .expects(
          HandleCommandRequest()
            .withCommand(cmd.command.asInstanceOf[Any])
            .withCurrentState(priorState.getCurrentState)
            .withMeta(
              cos_common
                .MetaData()
                .withData(priorEventMeta.data)
                .withRevisionDate(priorEventMeta.getRevisionDate)
                .withRevisionNumber(priorEventMeta.revisionNumber)
            )
        )
        .throws(new GrpcServiceException(Status.INVALID_ARGUMENT))

      // let us execute the request
      val cmdhandler = new ChiefOfStateCommandHandler(null, mockGrpcClient, handlerSetting)
      val result: Try[CommandHandlerResponse] = cmdhandler.handle(cmd, priorState, priorEventMeta)
      result shouldBe (Success(
        CommandHandlerResponse()
          .withFailedResponse(
            FailedCommandHandlerResponse()
              .withReason(Status.INVALID_ARGUMENT.toString)
              .withCause(FailureCause.InternalError)
          )
      ))
    }

    "handle broken command handler as expected" in {
      val priorState: State = State.defaultInstance
      val priorEventMeta: MetaData = MetaData.defaultInstance
      val accouuntId: String = UUID.randomUUID.toString
      val accountNumber: String = "123445"

      val stateProto: String = ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance))
      val eventsProtos: Seq[String] =
        Seq(ChiefOfStateHelper.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))

      val handlerSetting: ChiefOfStateHandlerSetting = ChiefOfStateHandlerSetting(stateProto, eventsProtos)

      val cmd = Command(
        Any.pack(
          OpenAccount()
            .withAccountNumber(accountNumber)
            .withAccountUuid(accouuntId)
        ),
        null, // ignore the actor ref in this test
        Map.empty
      )

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[WriteSideHandlerServiceClient]

      (mockGrpcClient
        .handleCommand(_: HandleCommandRequest))
        .expects(
          HandleCommandRequest()
            .withCommand(cmd.command.asInstanceOf[Any])
            .withCurrentState(priorState.getCurrentState)
            .withMeta(
              cos_common
                .MetaData()
                .withData(priorEventMeta.data)
                .withRevisionDate(priorEventMeta.getRevisionDate)
                .withRevisionNumber(priorEventMeta.revisionNumber)
            )
        )
        .throws(new RuntimeException("broken"))

      // let us execute the request
      val cmdhandler = new ChiefOfStateCommandHandler(null, mockGrpcClient, handlerSetting)
      val result: Try[CommandHandlerResponse] = cmdhandler.handle(cmd, priorState, priorEventMeta)
      result shouldBe (Success(
        CommandHandlerResponse()
          .withFailedResponse(
            FailedCommandHandlerResponse()
              .withReason(
                new GrpcServiceException(
                  Status.INTERNAL.withDescription(
                    s"Error occurred. Unable to handle command ${cmd.command.getClass.getCanonicalName}"
                  )
                ).toString
              )
              .withCause(FailureCause.InternalError)
          )
      ))
    }
  }
}
