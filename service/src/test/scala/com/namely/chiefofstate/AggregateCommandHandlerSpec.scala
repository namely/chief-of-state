package com.namely.chiefofstate

import java.util.UUID

import akka.grpc.GrpcServiceException
import com.google.protobuf.any.Any
import com.namely.protobuf.chief_of_state.common.{MetaData => _}
import com.namely.protobuf.chief_of_state.common
import com.namely.protobuf.chief_of_state.persistence.{Event, State}
import com.namely.protobuf.chief_of_state.tests.{Account, AccountOpened, OpenAccount}
import com.namely.protobuf.chief_of_state.writeside._
import com.namely.protobuf.chief_of_state.writeside.HandleCommandResponse.ResponseType
import com.namely.protobuf.chief_of_state.service.GetStateRequest
import io.grpc.{Status, StatusRuntimeException}
import io.superflat.lagompb.protobuf.core._
import io.superflat.lagompb.testkit.LagompbSpec
import io.superflat.lagompb.Command
import org.scalamock.scalatest.MockFactory

import scala.concurrent.Future
import scala.util.{Success, Try}

class AggregateCommandHandlerSpec extends LagompbSpec with MockFactory {

  "main commandHandler" should {
    "call the local state handler when given a GetStateRequest" in {
      val stateProto: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance)))
      val eventsProtos: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))
      val handlerSetting: HandlerSetting = HandlerSetting(stateProto, eventsProtos)

      val cmd = Command(
        GetStateRequest.defaultInstance,
        null, // ignore the actor ref in this test
        Map.empty
      )

      val priorState: State = State().withCurrentState(Any.pack(Account.defaultInstance))
      val priorEventMeta: MetaData = MetaData.defaultInstance

      // let us create a mock instance of the handler service client
      // this will always fail, but should not be called
      val mockGrpcClient = mock[WriteSideHandlerServiceClient]

      (mockGrpcClient
        .handleCommand(_: HandleCommandRequest))
        .expects(*)
        .throws(new RuntimeException("this never throws"))
        .never()

      val cmdhandler = new AggregateCommandHandler(null, mockGrpcClient, handlerSetting)
      val result: Try[CommandHandlerResponse] = cmdhandler.handle(cmd, priorState, priorEventMeta)

      result shouldBe(
        Success(
          CommandHandlerResponse()
            .withSuccessResponse(
              SuccessCommandHandlerResponse()
                .withNoEvent(com.google.protobuf.empty.Empty.defaultInstance)
            )
        )
      )
    }

    "call the remote handler when given a normal request" in {
      val stateProto: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance)))
      val eventsProtos: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))
      val handlerSetting: HandlerSetting = HandlerSetting(stateProto, eventsProtos)

      val cmd = Command(
        Any.pack(AccountOpened.defaultInstance),
        null, // ignore the actor ref in this test
        Map.empty
      )

      // let us create a mock instance of the handler service client
      // this will always fail, but should not be called
      val mockGrpcClient = mock[WriteSideHandlerServiceClient]

      (mockGrpcClient
        .handleCommand(_: HandleCommandRequest))
        .expects(*)
        .throws(new RuntimeException("this throws"))

      val cmdhandler = new AggregateCommandHandler(null, mockGrpcClient, handlerSetting)

      val result: Try[CommandHandlerResponse] = cmdhandler.handle(
        cmd,
        State.defaultInstance,
        MetaData.defaultInstance
      )

      result shouldBe(
        Success(
          CommandHandlerResponse()
            .withFailedResponse(
              FailedCommandHandlerResponse()
                .withReason("Critical error occurred handling command com.google.protobuf.any.Any, this throws")
                .withCause(FailureCause.InternalError)
            )
        )
      )
    }
  }

  "gRPC remote handler" should {

    "handle command successfully as expected with an event to persist" in {
      val priorState: State = State.defaultInstance
      val priorEventMeta: MetaData = MetaData.defaultInstance
      val stateProto: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance)))
      val eventsProtos: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))
      val handlerSetting: HandlerSetting = HandlerSetting(stateProto, eventsProtos)

      val cmd = Command(
        Any.pack(OpenAccount.defaultInstance),
        null, // ignore the actor ref in this test
        Map.empty
      )

      val event = AccountOpened()
        .withAccountNumber("123445")
        .withAccountUuid(UUID.randomUUID.toString)

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[WriteSideHandlerServiceClient]

      (mockGrpcClient
        .handleCommand(_: HandleCommandRequest))
        .expects(
          HandleCommandRequest()
            .withCommand(cmd.command.asInstanceOf[Any])
            .withCurrentState(priorState.getCurrentState)
            .withMeta(
              common
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
      val cmdhandler = new AggregateCommandHandler(null, mockGrpcClient, handlerSetting)
      val result: CommandHandlerResponse = cmdhandler.handleRemoteCommand(cmd, priorState, priorEventMeta)

      result shouldBe (
        CommandHandlerResponse()
          .withSuccessResponse(
            SuccessCommandHandlerResponse()
              .withEvent(Any.pack(Event().withEvent(Any.pack(event))))
          )
      )
    }

    "handle command when event type is not specified in handler settings as expected" in {
      val priorState: State = State.defaultInstance
      val priorEventMeta: MetaData = MetaData.defaultInstance
      val stateProto: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance)))
      val eventsProtos: Seq[String] = Seq("namely.com.SomeEvent")
      val handlerSetting: HandlerSetting = HandlerSetting(stateProto, eventsProtos)

      val cmd = Command(
        Any.pack(OpenAccount.defaultInstance),
        null, // ignore the actor ref in this test
        Map.empty
      )

      val event = AccountOpened.defaultInstance

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[WriteSideHandlerServiceClient]

      (mockGrpcClient
        .handleCommand(_: HandleCommandRequest))
        .expects(*)
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
      val cmdhandler = new AggregateCommandHandler(null, mockGrpcClient, handlerSetting)
      val result: CommandHandlerResponse = cmdhandler.handleRemoteCommand(cmd, priorState, priorEventMeta)

      result shouldBe (
        CommandHandlerResponse()
          .withFailedResponse(
            FailedCommandHandlerResponse()
              .withReason("received unknown event type chief_of_state.AccountOpened")
              .withCause(FailureCause.ValidationError)
          )
      )

    }

    "handle command successfully as expected with no event to persist" in {
      val priorState: State = State.defaultInstance
      val priorEventMeta: MetaData = MetaData.defaultInstance
      val stateProto: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance)))
      val eventsProtos: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))
      val handlerSetting: HandlerSetting = HandlerSetting(stateProto, eventsProtos)

      val cmd = Command(
        Any.pack(OpenAccount.defaultInstance),
        null, // ignore the actor ref in this test
        Map.empty
      )

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[WriteSideHandlerServiceClient]

      (mockGrpcClient
        .handleCommand(_: HandleCommandRequest))
        .expects(*)
        .returning(
          Future.successful(
            HandleCommandResponse()
              .withReply(Reply.defaultInstance)
          )
        )

      // let us execute the request
      val cmdhandler = new AggregateCommandHandler(null, mockGrpcClient, handlerSetting)
      val result: CommandHandlerResponse = cmdhandler.handleRemoteCommand(cmd, priorState, priorEventMeta)
      result shouldBe (
        CommandHandlerResponse()
          .withSuccessResponse(
            SuccessCommandHandlerResponse()
              .withNoEvent(com.google.protobuf.empty.Empty.defaultInstance)
          )
      )
    }

    "handle wrong successful response as expected" in {
      val priorState: State = State.defaultInstance
      val priorEventMeta: MetaData = MetaData.defaultInstance
      val stateProto: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance)))
      val eventsProtos: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))
      val handlerSetting: HandlerSetting = HandlerSetting(stateProto, eventsProtos)
      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[WriteSideHandlerServiceClient]

      (mockGrpcClient
        .handleCommand(_: HandleCommandRequest))
        .expects(*)
        .returning(
          Future.successful(
            HandleCommandResponse()
              .withResponseType(ResponseType.Empty)
          )
        )

      val cmd = Command(
        Any.pack(OpenAccount.defaultInstance),
        null, // ignore the actor ref in this test
        Map.empty
      )

      // let us execute the request
      val cmdhandler = new AggregateCommandHandler(null, mockGrpcClient, handlerSetting)
      val result: Try[CommandHandlerResponse] = cmdhandler.handle(cmd, priorState, priorEventMeta)
      result shouldBe (Success(
        CommandHandlerResponse()
          .withFailedResponse(
            FailedCommandHandlerResponse()
              .withReason("command handler returned malformed event")
              .withCause(FailureCause.InternalError)
          )
      ))
    }

    "handle failed response as expected" in {
      val priorState: State = State.defaultInstance
      val priorEventMeta: MetaData = MetaData.defaultInstance
      val stateProto: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance)))
      val eventsProtos: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))
      val handlerSetting: HandlerSetting = HandlerSetting(stateProto, eventsProtos)
      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[WriteSideHandlerServiceClient]

      (mockGrpcClient
        .handleCommand(_: HandleCommandRequest))
        .expects(*)
        .returning(Future.failed(new GrpcServiceException(Status.NOT_FOUND)))

      // let us execute the request
      val cmdhandler = new AggregateCommandHandler(null, mockGrpcClient, handlerSetting)

      val cmd = Command(
        Any.pack(OpenAccount.defaultInstance),
        null, // ignore the actor ref in this test
        Map.empty
      )

      val result: CommandHandlerResponse = cmdhandler.handleRemoteCommand(cmd, priorState, priorEventMeta)

      result shouldBe (
        CommandHandlerResponse()
          .withFailedResponse(
            FailedCommandHandlerResponse()
              .withReason(Status.NOT_FOUND.toString)
              .withCause(FailureCause.InternalError)
          )
      )
    }

    "handle failed validations sent by command handler" in {
      val priorState: State = State.defaultInstance
      val priorEventMeta: MetaData = MetaData.defaultInstance
      val stateProto: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance)))
      val eventsProtos: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))
      val handlerSetting: HandlerSetting = HandlerSetting(stateProto, eventsProtos)

      val cmd = Command(
        Any.pack(OpenAccount.defaultInstance),
        null, // ignore the actor ref in this test
        Map.empty
      )

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[WriteSideHandlerServiceClient]

      val badStatus: Status = Status.INVALID_ARGUMENT.withDescription("very invalid")

      (mockGrpcClient
        .handleCommand(_: HandleCommandRequest))
        .expects(*)
        .throws(new StatusRuntimeException(badStatus))

      // let us execute the request
      val cmdhandler = new AggregateCommandHandler(null, mockGrpcClient, handlerSetting)
      val result: CommandHandlerResponse = cmdhandler.handleRemoteCommand(cmd, priorState, priorEventMeta)
      result.getFailedResponse.reason.contains(badStatus.getDescription) shouldBe(true)
      result.getFailedResponse.reason.contains(badStatus.getCode.name) shouldBe(true)
      result.getFailedResponse.cause shouldBe(FailureCause.ValidationError)
    }

    "handle gRPC internal errors from command handler" in {
      val priorState: State = State.defaultInstance
      val priorEventMeta: MetaData = MetaData.defaultInstance
      val stateProto: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance)))
      val eventsProtos: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))
      val handlerSetting: HandlerSetting = HandlerSetting(stateProto, eventsProtos)

      val cmd = Command(
        Any.pack(OpenAccount.defaultInstance),
        null, // ignore the actor ref in this test
        Map.empty
      )

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[WriteSideHandlerServiceClient]

      val badStatus: Status = Status.INTERNAL.withDescription("super broken")

      (mockGrpcClient
        .handleCommand(_: HandleCommandRequest))
        .expects(*)
        .throws(new StatusRuntimeException(badStatus))

      // let us execute the request
      val cmdhandler = new AggregateCommandHandler(null, mockGrpcClient, handlerSetting)
      val result: CommandHandlerResponse = cmdhandler.handleRemoteCommand(cmd, priorState, priorEventMeta)
      result.getFailedResponse.reason.contains(badStatus.getDescription) shouldBe(true)
      result.getFailedResponse.reason.contains(badStatus.getCode.name) shouldBe(true)
      result.getFailedResponse.cause shouldBe(FailureCause.InternalError)
    }

    "handles akka gRPC exceptions" in {
      val stateProto: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance)))
      val eventsProtos: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))
      val handlerSetting: HandlerSetting = HandlerSetting(stateProto, eventsProtos)

      val cmd = Command(
        Any.pack(OpenAccount.defaultInstance),
        null, // ignore the actor ref in this test
        Map.empty
      )

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[WriteSideHandlerServiceClient]
      val badStatus: Status = Status.INTERNAL.withDescription("grpc broken")

      (mockGrpcClient
        .handleCommand(_: HandleCommandRequest))
        .expects(*)
        .throws(new GrpcServiceException(status=badStatus))

      // let us execute the request
      val cmdhandler = new AggregateCommandHandler(null, mockGrpcClient, handlerSetting)
      val result: CommandHandlerResponse = cmdhandler.handleRemoteCommand(cmd, State.defaultInstance, MetaData.defaultInstance)

      result shouldBe(
        CommandHandlerResponse()
          .withFailedResponse(
            FailedCommandHandlerResponse()
              .withReason(badStatus.toString())
              .withCause(FailureCause.InternalError)
          )
      )
    }

    "handles a critical grpc failure" in {
      val stateProto: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance)))
      val eventsProtos: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))
      val handlerSetting: HandlerSetting = HandlerSetting(stateProto, eventsProtos)

      val cmd = Command(
        Any.pack(OpenAccount.defaultInstance),
        null, // ignore the actor ref in this test
        Map.empty
      )

      // let us create a mock instance of the handler service client
      val mockGrpcClient = mock[WriteSideHandlerServiceClient]

      (mockGrpcClient
        .handleCommand(_: HandleCommandRequest))
        .expects(*)
        .throws(new RuntimeException("broken"))

      // let us execute the request
      val cmdhandler = new AggregateCommandHandler(null, mockGrpcClient, handlerSetting)
      val result: CommandHandlerResponse = cmdhandler.handleRemoteCommand(cmd, State.defaultInstance, MetaData.defaultInstance)

      result.handlerResponse.isFailedResponse shouldBe(true)

      result shouldBe(
        CommandHandlerResponse()
          .withFailedResponse(
            FailedCommandHandlerResponse()
              .withReason("Critical error occurred handling command com.google.protobuf.any.Any, broken")
              .withCause(FailureCause.InternalError)
          )
      )
    }
  }

  "GetStateRequest handler" should {
    "return the current state when entity exists" in {

      // create a CommandHandler with a mock client
      val stateProto: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance)))
      val eventsProtos: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))
      val handlerSetting: HandlerSetting = HandlerSetting(stateProto, eventsProtos)
      val mockGrpcClient = mock[WriteSideHandlerServiceClient]
      val cmdhandler = new AggregateCommandHandler(null, mockGrpcClient, handlerSetting)

      val someAccount = Account()
        .withAccountUuid(UUID.randomUUID.toString)
        .withAccountNumber("12345")

      val priorState: State = State()
        .withCurrentState(Any.pack(someAccount))

      val priorEventMeta: MetaData = MetaData.defaultInstance

      val cmd = GetStateRequest(entityId = "x")

      val actual: CommandHandlerResponse = cmdhandler.handleGetCommand(cmd, priorState, priorEventMeta)

      actual.handlerResponse.isSuccessResponse shouldBe(true)
      actual.handlerResponse.successResponse.map(_.response.isNoEvent) shouldBe(Some(true))
    }

    "return a failure when prior state is not found" in {
      // create a CommandHandler with a mock client
      val stateProto: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance)))
      val eventsProtos: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))
      val handlerSetting: HandlerSetting = HandlerSetting(stateProto, eventsProtos)
      val mockGrpcClient = mock[WriteSideHandlerServiceClient]
      val cmdhandler = new AggregateCommandHandler(null, mockGrpcClient, handlerSetting)

      val priorEventMeta: MetaData = MetaData.defaultInstance
      val cmd = GetStateRequest(entityId = "x")
      val priorState = State.defaultInstance
      val actual: CommandHandlerResponse = cmdhandler.handleGetCommand(cmd, priorState, priorEventMeta)
      actual.handlerResponse.isFailedResponse shouldBe(true)
      actual.handlerResponse.failedResponse.map(_.reason) shouldBe (Some("entity not found"))
    }
  }
}
