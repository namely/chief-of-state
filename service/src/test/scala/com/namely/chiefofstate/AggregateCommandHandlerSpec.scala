package com.namely.chiefofstate

import java.util.UUID

import akka.grpc.GrpcServiceException
import akka.grpc.scaladsl.SingleResponseRequestBuilder
import com.google.protobuf.any.Any
import com.google.protobuf.ByteString
import com.google.protobuf.wrappers.StringValue
import com.namely.protobuf.chief_of_state.v1beta1.internal.RemoteCommand
import com.namely.protobuf.chief_of_state.v1beta1.service.GetStateRequest
import com.namely.protobuf.chief_of_state.v1beta1.tests.{Account, AccountOpened, OpenAccount}
import com.namely.protobuf.chief_of_state.v1beta1.writeside._
import com.namely.protobuf.chief_of_state.v1beta1.writeside.HandleCommandResponse.ResponseType
import com.namely.chiefofstate.config.HandlerSetting
import io.grpc.{Status, StatusRuntimeException}
import io.superflat.lagompb.Command
import io.superflat.lagompb.protobuf.v1.core.{
  CommandHandlerResponse,
  FailedCommandHandlerResponse,
  FailureCause,
  SuccessCommandHandlerResponse,
  MetaData => LagompbMetaData
}
import io.superflat.lagompb.testkit.BaseSpec
import org.scalamock.scalatest.MockFactory

import scala.concurrent.Future
import scala.util.{Success, Try}

class AggregateCommandHandlerSpec extends BaseSpec with MockFactory {

  /**
   * helper to return a mock request builder
   *
   * @return mock request builder
   */
  def getMockRequestBuilder: SingleResponseRequestBuilder[HandleCommandRequest, HandleCommandResponse] = {
    mock[SingleResponseRequestBuilder[HandleCommandRequest, HandleCommandResponse]]
  }

  /**
   * helper method to generate mock gRPC client with provided
   * request builder
   *
   * @param requestBuilder mock request builder
   * @return mock grpc client
   */
  def getMockClient(
    requestBuilder: SingleResponseRequestBuilder[HandleCommandRequest, HandleCommandResponse]
  ): WriteSideHandlerServiceClient = {
    val mockGrpcClient = mock[WriteSideHandlerServiceClient]

    (mockGrpcClient.handleCommand _: () => SingleResponseRequestBuilder[HandleCommandRequest, HandleCommandResponse])
      .expects()
      .returning(requestBuilder)

    mockGrpcClient
  }

  val testHandlerSetting: HandlerSetting = {
    val stateProto: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance)))
    val eventsProtos: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))
    HandlerSetting(enableProtoValidations = true, stateProto, eventsProtos)
  }

  "main commandHandler" should {
    "call the local state handler when given a GetStateRequest" in {
      val cmd = Command(
        GetStateRequest.defaultInstance,
        null, // ignore the actor ref in this test
        Map.empty
      )

      val priorState: State = State().withCurrentState(Any.pack(Account.defaultInstance))
      val priorEventMeta: LagompbMetaData = LagompbMetaData.defaultInstance

      val cmdhandler: AggregateCommandHandler = new AggregateCommandHandler(null, null, testHandlerSetting)
      val result: Try[CommandHandlerResponse] = cmdhandler.handle(cmd, priorState, priorEventMeta)

      result shouldBe Success(
        CommandHandlerResponse()
          .withSuccessResponse(
            SuccessCommandHandlerResponse()
              .withNoEvent(com.google.protobuf.empty.Empty.defaultInstance)
          )
      )
    }

    "call the remote handler when given a RemoteCommand" in {
      val innerCmd = Any.pack(AccountOpened.defaultInstance)
      val outerCmd = RemoteCommand().withCommand(innerCmd)

      val cmd = Command(
        outerCmd,
        null, // ignore the actor ref in this test
        Map.empty
      )

      // let us create a mock instance of the handler service client
      // this will always fail, but should not be called
      val requestBuilder = getMockRequestBuilder

      (requestBuilder.invoke _)
        .expects(*)
        .throws(new RuntimeException("this throws"))

      // let us create a mock instance of the handler service client
      val mockGrpcClient = getMockClient(requestBuilder)

      val cmdhandler = new AggregateCommandHandler(null, mockGrpcClient, testHandlerSetting)

      val result: Try[CommandHandlerResponse] = cmdhandler.handle(
        cmd,
        State.defaultInstance,
        LagompbMetaData.defaultInstance
      )

      result shouldBe Success(
        CommandHandlerResponse()
          .withFailedResponse(
            FailedCommandHandlerResponse()
              .withReason("Critical error occurred handling command, this throws")
              .withCause(FailureCause.INTERNAL_ERROR)
          )
      )
    }

    "fail when its an unknown type" in {
      val cmd = Command(
        StringValue("oops"),
        null, // ignore the actor ref in this test
        Map.empty
      )
      val cmdhandler = new AggregateCommandHandler(null, null, testHandlerSetting)
      val actual = cmdhandler.handle(
        cmd,
        State.defaultInstance,
        LagompbMetaData.defaultInstance
      )
      actual.failed.get.getMessage.contains("unhandled command type")
    }
  }

  "gRPC remote handler" should {

    "make remote call with headers" in {
      val header1 = RemoteCommand
        .Header()
        .withKey("x-cos-key-1")
        .withStringValue("value 1")

      val header2 = RemoteCommand
        .Header()
        .withKey("x-cos-key-2")
        .withBytesValue(ByteString.copyFromUtf8("value 2"))

      val cmd = RemoteCommand()
        .withCommand(Any.pack(OpenAccount.defaultInstance))
        .addHeaders(header1, header2)

      val event = AccountOpened()
        .withAccountNumber("123445")
        .withAccountUuid(UUID.randomUUID.toString)

      val currentState: Any = Any.pack(Account.defaultInstance)
      val currentMeta: LagompbMetaData = LagompbMetaData.defaultInstance

      // let us create a mock instance of the handler service client
      val mockRequestBuilder = getMockRequestBuilder
      val mockGrpcClient = getMockClient(mockRequestBuilder)

      (mockRequestBuilder
        .addHeader(_: String, _: String))
        .expects(header1.key, header1.getStringValue)
        .returning(mockRequestBuilder)

      (mockRequestBuilder
        .addHeader(_: String, _: akka.util.ByteString))
        .expects(header2.key, akka.util.ByteString(header2.getBytesValue.toByteArray))
        .returning(mockRequestBuilder)

      (mockRequestBuilder
        .invoke(_: HandleCommandRequest))
        .expects(
          HandleCommandRequest()
            .withCommand(cmd.getCommand)
            .withCurrentState(currentState.getCurrentState)
            .withMeta(Util.toCosMetaData(currentMeta))
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

      val cmdhandler = new AggregateCommandHandler(null, mockGrpcClient, testHandlerSetting)

      val result: CommandHandlerResponse = cmdhandler.handleRemoteCommand(
        cmd,
        currentState,
        currentMeta
      )

      val expected: CommandHandlerResponse = CommandHandlerResponse()
        .withSuccessResponse(
          SuccessCommandHandlerResponse()
            .withEvent(Any.pack(Event().withEvent(Any.pack(event))))
        )

      result shouldBe expected
    }

    "fail when headers are invalid" in {
      val badHeader = RemoteCommand
        .Header()
        .withKey("x-cos-key-1")
        .withValue(RemoteCommand.Header.Value.Empty)

      val cmd = RemoteCommand()
        .withCommand(Any.pack(OpenAccount.defaultInstance))
        .addHeaders(badHeader)

      // let us create a mock instance of the handler service client
      val mockRequestBuilder = getMockRequestBuilder
      val mockGrpcClient = getMockClient(mockRequestBuilder)

      val cmdhandler = new AggregateCommandHandler(null, mockGrpcClient, testHandlerSetting)

      val result: CommandHandlerResponse =
        cmdhandler.handleRemoteCommand(
          cmd,
          State.defaultInstance,
          LagompbMetaData.defaultInstance
        )

      result.getFailedResponse.reason.contains("unhandled gRPC header type") shouldBe true
    }

    "handle command successfully as expected with an event to persist" in {
      val priorState: State = State.defaultInstance
      val priorEventMeta: LagompbMetaData = LagompbMetaData.defaultInstance

      val cmd = RemoteCommand()
        .withCommand(Any.pack(OpenAccount.defaultInstance))

      val event = AccountOpened()
        .withAccountNumber("123445")
        .withAccountUuid(UUID.randomUUID.toString)

      // let us create a mock instance of the handler service client
      val mockRequestBuilder = getMockRequestBuilder
      val mockGrpcClient = getMockClient(mockRequestBuilder)

      (mockRequestBuilder
        .invoke(_: HandleCommandRequest))
        .expects(
          HandleCommandRequest()
            .withCommand(cmd.getCommand)
            .withCurrentState(priorState.getCurrentState)
            .withMeta(Util.toCosMetaData(priorEventMeta))
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
      val cmdhandler = new AggregateCommandHandler(null, mockGrpcClient, testHandlerSetting)
      val result: CommandHandlerResponse = cmdhandler.handleRemoteCommand(cmd, priorState, priorEventMeta)

      result shouldBe CommandHandlerResponse()
        .withSuccessResponse(
          SuccessCommandHandlerResponse()
            .withEvent(Any.pack(Event().withEvent(Any.pack(event))))
        )
    }
  }
  "handleRemoteResponseSuccess" should {

    "handle a successful persist event" in {
      val event = AccountOpened()
        .withAccountNumber("123445")
        .withAccountUuid(UUID.randomUUID.toString)

      val response = HandleCommandResponse()
        .withPersistAndReply(
          PersistAndReply()
            .withEvent(Any.pack(event))
        )

      val cmdhandler = new AggregateCommandHandler(null, null, testHandlerSetting)
      val result: CommandHandlerResponse = cmdhandler.handleRemoteResponseSuccess(response)

      result shouldBe CommandHandlerResponse()
        .withSuccessResponse(
          SuccessCommandHandlerResponse()
            .withEvent(Any.pack(Event().withEvent(Any.pack(event))))
        )

    }

    "handle command when event type is not specified in handler settings as expected" in {
      val badResponse =
        HandleCommandResponse()
          .withPersistAndReply(
            PersistAndReply()
              .withEvent(Any.pack(AccountOpened.defaultInstance))
          )

      // let us execute the request
      val badHandlerSettings: HandlerSetting = HandlerSetting(enableProtoValidations = true, Seq(), Seq())
      val cmdhandler = new AggregateCommandHandler(null, null, badHandlerSettings)
      val result: CommandHandlerResponse = cmdhandler.handleRemoteResponseSuccess(badResponse)

      result shouldBe CommandHandlerResponse()
        .withFailedResponse(
          FailedCommandHandlerResponse()
            .withReason("received unknown event type chief_of_state.AccountOpened")
            .withCause(FailureCause.VALIDATION_ERROR)
        )

    }

    "handle command when event type in handler settings is disabled as expected" in {

      val event = AccountOpened()
        .withAccountNumber("123445")
        .withAccountUuid(UUID.randomUUID.toString)

      val response = HandleCommandResponse()
        .withPersistAndReply(
          PersistAndReply()
            .withEvent(Any.pack(event))
        )

      // set enableProtoValidations to false and not provide event and state protos
      val handlerSettings: HandlerSetting = HandlerSetting(enableProtoValidations = false, Seq(), Seq())
      val cmdhandler = new AggregateCommandHandler(null, null, handlerSettings)
      val result: CommandHandlerResponse = cmdhandler.handleRemoteResponseSuccess(response)

      result shouldBe CommandHandlerResponse()
        .withSuccessResponse(
          SuccessCommandHandlerResponse()
            .withEvent(Any.pack(Event().withEvent(Any.pack(event))))
        )

    }

    "handle command successfully as expected with no event to persist" in {
      // let us execute the request
      val cmdhandler = new AggregateCommandHandler(null, null, testHandlerSetting)

      val response = HandleCommandResponse().withReply(Reply.defaultInstance)
      val result: CommandHandlerResponse = cmdhandler.handleRemoteResponseSuccess(response)

      result shouldBe CommandHandlerResponse()
        .withSuccessResponse(
          SuccessCommandHandlerResponse()
            .withNoEvent(com.google.protobuf.empty.Empty.defaultInstance)
        )
    }

    "handle wrong successful response as expected" in {
      val cmdhandler = new AggregateCommandHandler(null, null, testHandlerSetting)
      // define a response that will fail
      val response = HandleCommandResponse().withResponseType(ResponseType.Empty)
      val result: CommandHandlerResponse = cmdhandler.handleRemoteResponseSuccess(response)

      result shouldBe CommandHandlerResponse()
        .withFailedResponse(
          FailedCommandHandlerResponse()
            .withReason(
              s"command handler returned malformed event, ${HandleCommandResponse.ResponseType.Empty.getClass.getName}"
            )
            .withCause(FailureCause.INTERNAL_ERROR)
        )
    }
  }

  "handleRemoteResponseFailure" should {

    "handle failed response as expected" in {
      val cmdhandler = new AggregateCommandHandler(null, null, testHandlerSetting)

      val exception = new GrpcServiceException(Status.NOT_FOUND)
      val result: CommandHandlerResponse = cmdhandler.handleRemoteResponseFailure(exception)

      result shouldBe CommandHandlerResponse()
        .withFailedResponse(
          FailedCommandHandlerResponse()
            .withReason(Status.NOT_FOUND.toString)
            .withCause(FailureCause.INTERNAL_ERROR)
        )
    }

    "handle failed validations sent by command handler" in {
      val badStatus: Status = Status.INVALID_ARGUMENT.withDescription("very invalid")
      val exception: StatusRuntimeException = new StatusRuntimeException(badStatus)
      val cmdhandler: AggregateCommandHandler = new AggregateCommandHandler(null, null, testHandlerSetting)
      val result: CommandHandlerResponse = cmdhandler.handleRemoteResponseFailure(exception)

      result.getFailedResponse.reason.contains(badStatus.getDescription) shouldBe true
      result.getFailedResponse.reason.contains(badStatus.getCode.name) shouldBe true
      result.getFailedResponse.cause shouldBe FailureCause.VALIDATION_ERROR
    }

    "handle gRPC internal errors from command handler" in {
      val badStatus: Status = Status.INTERNAL.withDescription("super broken")
      val exception: StatusRuntimeException = new StatusRuntimeException(badStatus)
      val cmdhandler: AggregateCommandHandler = new AggregateCommandHandler(null, null, testHandlerSetting)
      val result: CommandHandlerResponse = cmdhandler.handleRemoteResponseFailure(exception)

      result.getFailedResponse.reason.contains(badStatus.getDescription) shouldBe true
      result.getFailedResponse.reason.contains(badStatus.getCode.name) shouldBe true
      result.getFailedResponse.cause shouldBe FailureCause.INTERNAL_ERROR
    }

    "handle akka gRPC exceptions" in {
      val badStatus: Status = Status.INTERNAL.withDescription("grpc broken")
      val exception: GrpcServiceException = new GrpcServiceException(status = badStatus)
      val cmdhandler: AggregateCommandHandler = new AggregateCommandHandler(null, null, testHandlerSetting)
      val result: CommandHandlerResponse = cmdhandler.handleRemoteResponseFailure(exception)

      result shouldBe CommandHandlerResponse()
        .withFailedResponse(
          FailedCommandHandlerResponse()
            .withReason(badStatus.toString)
            .withCause(FailureCause.INTERNAL_ERROR)
        )
    }

    "handles a critical grpc failure" in {
      val exception: RuntimeException = new RuntimeException("broken")
      val cmdhandler: AggregateCommandHandler = new AggregateCommandHandler(null, null, testHandlerSetting)
      val result: CommandHandlerResponse = cmdhandler.handleRemoteResponseFailure(exception)

      result.handlerResponse.isFailedResponse shouldBe true

      result shouldBe CommandHandlerResponse()
        .withFailedResponse(
          FailedCommandHandlerResponse()
            .withReason("Critical error occurred handling command, broken")
            .withCause(FailureCause.INTERNAL_ERROR)
        )
    }
  }

  "GetStateRequest handler" should {
    "return the current state when entity exists" in {

      // create a CommandHandler with a mock client
      val stateProto: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance)))
      val eventsProtos: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))
      val handlerSetting: HandlerSetting = HandlerSetting(enableProtoValidations = true, stateProto, eventsProtos)
      val mockGrpcClient = mock[WriteSideHandlerServiceClient]
      val cmdhandler = new AggregateCommandHandler(null, mockGrpcClient, handlerSetting)

      val someAccount = Account()
        .withAccountUuid(UUID.randomUUID.toString)
        .withAccountNumber("12345")

      val priorState: State = State()
        .withCurrentState(Any.pack(someAccount))

      val priorEventMeta: LagompbMetaData = LagompbMetaData.defaultInstance

      val cmd = GetStateRequest(entityId = "x")

      val actual: CommandHandlerResponse = cmdhandler.handleGetCommand(cmd, priorState, priorEventMeta)

      actual.handlerResponse.isSuccessResponse shouldBe true
      actual.handlerResponse.successResponse.map(_.response.isNoEvent) shouldBe Some(true)
    }

    "return a failure when prior state is not found" in {
      // create a CommandHandler with a mock client
      val stateProto: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(Account.defaultInstance)))
      val eventsProtos: Seq[String] = Seq(Util.getProtoFullyQualifiedName(Any.pack(AccountOpened.defaultInstance)))
      val handlerSetting: HandlerSetting = HandlerSetting(enableProtoValidations = true, stateProto, eventsProtos)
      val mockGrpcClient: WriteSideHandlerServiceClient = mock[WriteSideHandlerServiceClient]
      val cmdhandler: AggregateCommandHandler = new AggregateCommandHandler(null, mockGrpcClient, handlerSetting)

      val priorEventMeta: LagompbMetaData = LagompbMetaData.defaultInstance
      val cmd = GetStateRequest(entityId = "x")
      val priorState = State.defaultInstance
      val actual: CommandHandlerResponse = cmdhandler.handleGetCommand(cmd, priorState, priorEventMeta)
      actual.handlerResponse.isFailedResponse shouldBe true
      actual.handlerResponse.failedResponse.map(_.reason) shouldBe Some("entity not found")
    }
  }
}
