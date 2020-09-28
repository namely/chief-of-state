package com.namely.chiefofstate

import java.util.UUID

import akka.grpc.GrpcServiceException
import akka.grpc.scaladsl.SingleResponseRequestBuilder
import com.google.protobuf.any.Any
import com.google.protobuf.ByteString
import com.google.protobuf.wrappers.StringValue
import com.namely.chiefofstate.config.HandlerSetting
import com.namely.chiefofstate.test.helpers.TestSpec
import com.namely.protobuf.chiefofstate.v1.internal.RemoteCommand
import com.namely.protobuf.chiefofstate.v1.service.GetStateRequest
import com.namely.protobuf.chiefofstate.v1.tests.{Account, AccountOpened, OpenAccount}
import com.namely.protobuf.chiefofstate.v1.writeside._
import io.grpc.{Status, StatusRuntimeException}
import io.superflat.lagompb.ProtosRegistry
import io.superflat.lagompb.protobuf.v1.core.{CommandHandlerResponse, FailureResponse, MetaData => LagompbMetaData}
import org.scalamock.scalatest.MockFactory

import scala.concurrent.Future
import scala.util.{Success, Try}

class AggregateCommandHandlerSpec extends TestSpec with MockFactory {

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
    "unmarshall in the parent handler" in {
      ProtosRegistry.load()
      val cmd = Any.pack(GetStateRequest.defaultInstance)
      val priorState: Any = Any.pack(Account.defaultInstance.withAccountNumber("123"))
      val priorEventMeta: LagompbMetaData = LagompbMetaData.defaultInstance
        .withRevisionNumber(1)

      val cmdhandler: AggregateCommandHandler = new AggregateCommandHandler(null, null, testHandlerSetting)
      val result: Try[CommandHandlerResponse] = cmdhandler.handle(cmd, priorState, priorEventMeta)

      result shouldBe Success(
        CommandHandlerResponse()
      )
    }

    "call the local state handler when given a GetStateRequest" in {
      val cmd = GetStateRequest.defaultInstance
      val priorState: Any = Any.pack(Account.defaultInstance)
      val priorEventMeta: LagompbMetaData = LagompbMetaData.defaultInstance
        .withRevisionNumber(1)

      val cmdhandler: AggregateCommandHandler = new AggregateCommandHandler(null, null, testHandlerSetting)
      val result: Try[CommandHandlerResponse] = cmdhandler.handleTyped(cmd, priorState, priorEventMeta)

      result shouldBe Success(
        CommandHandlerResponse()
      )
    }

    "call the remote handler when given a RemoteCommand" in {
      val innerCmd = Any.pack(AccountOpened.defaultInstance)
      val cmd = RemoteCommand().withCommand(innerCmd)
      val priorState: Any = Any.pack(Account.defaultInstance)
      val priorEventMeta: LagompbMetaData = LagompbMetaData.defaultInstance

      // let us create a mock instance of the handler service client
      // this will always fail, but should not be called
      val requestBuilder = getMockRequestBuilder

      (requestBuilder.invoke _)
        .expects(*)
        .throws(new RuntimeException("this throws"))

      // let us create a mock instance of the handler service client
      val mockGrpcClient = getMockClient(requestBuilder)

      val cmdhandler = new AggregateCommandHandler(null, mockGrpcClient, testHandlerSetting)

      val result: Try[CommandHandlerResponse] = cmdhandler.handleTyped(cmd, priorState, priorEventMeta)

      result shouldBe Success(
        CommandHandlerResponse()
          .withFailure(
            FailureResponse()
              .withCritical("Critical error occurred handling command, this throws")
          )
      )
    }

    "fail when its an unknown type" in {
      val cmd = Any.pack(StringValue("oops"))
      val priorState: Any = Any.pack(Account.defaultInstance)
      val priorEventMeta: LagompbMetaData = LagompbMetaData.defaultInstance
      val cmdhandler = new AggregateCommandHandler(null, null, testHandlerSetting)
      val actual = cmdhandler.handle(cmd, priorState, priorEventMeta)
      actual.failed.get.getMessage.contains("unhandled command type")
    }

    "fails in the typed handler for unknown types" in {
      val cmd = Any.pack(StringValue("oops"))
      val priorState: Any = Any.pack(Account.defaultInstance)
      val priorEventMeta: LagompbMetaData = LagompbMetaData.defaultInstance
      val cmdhandler = new AggregateCommandHandler(null, null, testHandlerSetting)
      val actual = cmdhandler.handleTyped(cmd, priorState, priorEventMeta)
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

      val event = Any.pack(
        AccountOpened()
          .withAccountNumber("123445")
          .withAccountUuid(UUID.randomUUID.toString)
      )

      val currentState: Any = Any.pack(Account.defaultInstance)
      val currentMeta: LagompbMetaData = LagompbMetaData.defaultInstance
        .withRevisionNumber(1)

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
            .withPriorState(currentState)
            .withPriorEventMeta(Util.toCosMetaData(currentMeta))
        )
        .returning(
          Future.successful(
            HandleCommandResponse()
              .withEvent(event)
          )
        )

      val cmdhandler = new AggregateCommandHandler(null, mockGrpcClient, testHandlerSetting)

      val result: CommandHandlerResponse = cmdhandler.handleRemoteCommand(
        cmd,
        currentState,
        currentMeta
      )

      val expected: CommandHandlerResponse = CommandHandlerResponse().withEvent(event)

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

      val priorState: Any = Any.pack(Account.defaultInstance)
      val priorEventMeta: LagompbMetaData = LagompbMetaData.defaultInstance

      // let us create a mock instance of the handler service client
      val mockRequestBuilder = getMockRequestBuilder
      val mockGrpcClient = getMockClient(mockRequestBuilder)

      val cmdhandler = new AggregateCommandHandler(null, mockGrpcClient, testHandlerSetting)

      val result: CommandHandlerResponse =
        cmdhandler.handleRemoteCommand(cmd, priorState, priorEventMeta)

      result.getFailure.getCritical.contains("header value must be string or bytes") shouldBe (true)
    }

    "handle command successfully as expected with an event to persist" in {
      val priorState: Any = Any.pack(Account.defaultInstance)
      val priorEventMeta: LagompbMetaData = LagompbMetaData.defaultInstance
        .withRevisionNumber(1)

      val innerCmd = Any.pack(OpenAccount.defaultInstance)
      val cmd = RemoteCommand()
        .withCommand(innerCmd)

      val event = Any.pack(
        AccountOpened()
          .withAccountNumber("123445")
          .withAccountUuid(UUID.randomUUID.toString)
      )

      // let us create a mock instance of the handler service client
      val mockRequestBuilder = getMockRequestBuilder
      val mockGrpcClient = getMockClient(mockRequestBuilder)

      (mockRequestBuilder
        .invoke(_: HandleCommandRequest))
        .expects(
          HandleCommandRequest()
            .withCommand(innerCmd)
            .withPriorState(priorState)
            .withPriorEventMeta(Util.toCosMetaData(priorEventMeta))
        )
        .returning(
          Future.successful(
            HandleCommandResponse()
              .withEvent(event)
          )
        )

      // let us execute the request
      val cmdhandler = new AggregateCommandHandler(null, mockGrpcClient, testHandlerSetting)
      val result: CommandHandlerResponse = cmdhandler.handleRemoteCommand(cmd, priorState, priorEventMeta)

      result shouldBe CommandHandlerResponse().withEvent(event)
    }
  }
  "handleRemoteResponseSuccess" should {

    "handle a successful persist event" in {
      val event = AccountOpened()
        .withAccountNumber("123445")
        .withAccountUuid(UUID.randomUUID.toString)

      val response = HandleCommandResponse().withEvent(Any.pack(event))
      val cmdhandler = new AggregateCommandHandler(null, null, testHandlerSetting)
      val result: CommandHandlerResponse = cmdhandler.handleRemoteResponseSuccess(response)

      result shouldBe CommandHandlerResponse().withEvent(Any.pack(event))
    }

    "handle command when event type is not specified in handler settings as expected" in {
      val badResponse =
        HandleCommandResponse()
          .withEvent(Any.pack(AccountOpened.defaultInstance))

      // let us execute the request
      val badHandlerSettings: HandlerSetting = HandlerSetting(enableProtoValidations = true, Seq(), Seq())
      val cmdhandler = new AggregateCommandHandler(null, null, badHandlerSettings)
      val result: CommandHandlerResponse = cmdhandler.handleRemoteResponseSuccess(badResponse)

      result shouldBe CommandHandlerResponse()
        .withFailure(
          FailureResponse()
            .withValidation("received unknown event type chief_of_state.v1.AccountOpened")
        )

    }

    "handle command when event type in handler settings is disabled as expected" in {

      val event = AccountOpened()
        .withAccountNumber("123445")
        .withAccountUuid(UUID.randomUUID.toString)

      val response = HandleCommandResponse().withEvent(Any.pack(event))

      // set enableProtoValidations to false and not provide event and state protos
      val handlerSettings: HandlerSetting = HandlerSetting(enableProtoValidations = false, Seq(), Seq())
      val cmdhandler = new AggregateCommandHandler(null, null, handlerSettings)
      val result: CommandHandlerResponse = cmdhandler.handleRemoteResponseSuccess(response)

      result shouldBe CommandHandlerResponse().withEvent(Any.pack(event))
    }

    "handle command successfully as expected with no event to persist" in {
      // let us execute the request
      val cmdhandler = new AggregateCommandHandler(null, null, testHandlerSetting)
      val response = HandleCommandResponse()
      val result: CommandHandlerResponse = cmdhandler.handleRemoteResponseSuccess(response)

      result shouldBe CommandHandlerResponse()
    }
  }

  "handleRemoteResponseFailure" should {

    "handle failed response as expected" in {
      val cmdhandler = new AggregateCommandHandler(null, null, testHandlerSetting)
      val exception = new StatusRuntimeException(Status.ABORTED)
      val result: CommandHandlerResponse = cmdhandler.handleRemoteResponseFailure(exception)
      result.getFailure.failureType.isCritical shouldBe (true)
    }

    "handle failed validations sent by command handler" in {
      val msg: String = "very invalid"
      val badStatus: Status = Status.INVALID_ARGUMENT.withDescription(msg)
      val exception: StatusRuntimeException = new StatusRuntimeException(badStatus)
      val cmdhandler: AggregateCommandHandler = new AggregateCommandHandler(null, null, testHandlerSetting)
      val result: CommandHandlerResponse = cmdhandler.handleRemoteResponseFailure(exception)
      result shouldBe (CommandHandlerResponse().withFailure(FailureResponse().withValidation(msg)))
    }

    "handle gRPC internal errors from command handler" in {
      val msg: String = "super broken"
      val badStatus: Status = Status.INTERNAL.withDescription(msg)
      val exception: StatusRuntimeException = new StatusRuntimeException(badStatus)
      val cmdhandler: AggregateCommandHandler = new AggregateCommandHandler(null, null, testHandlerSetting)
      val result: CommandHandlerResponse = cmdhandler.handleRemoteResponseFailure(exception)
      result shouldBe (CommandHandlerResponse().withFailure(FailureResponse().withCritical(msg)))
    }

    "handle akka gRPC exceptions" in {
      val msg: String = "grpc broken"
      val badStatus: Status = Status.INTERNAL.withDescription(msg)
      val exception: GrpcServiceException = new GrpcServiceException(status = badStatus)
      val cmdhandler: AggregateCommandHandler = new AggregateCommandHandler(null, null, testHandlerSetting)
      val result: CommandHandlerResponse = cmdhandler.handleRemoteResponseFailure(exception)
      result shouldBe (CommandHandlerResponse().withFailure(FailureResponse().withCritical(msg)))
    }

    "handles a critical grpc failure" in {
      val msg = "broken"
      val exception: RuntimeException = new RuntimeException(msg)
      val cmdhandler: AggregateCommandHandler = new AggregateCommandHandler(null, null, testHandlerSetting)
      val actual: CommandHandlerResponse = cmdhandler.handleRemoteResponseFailure(exception)
      val expected = CommandHandlerResponse()
        .withFailure(
          FailureResponse()
            .withCritical(s"Critical error occurred handling command, $msg")
        )
      actual shouldBe (expected)
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

      val priorEventMeta: LagompbMetaData = LagompbMetaData.defaultInstance.withRevisionNumber(1)

      val cmd = GetStateRequest(entityId = "x")

      val actual: CommandHandlerResponse = cmdhandler.handleGetCommand(cmd, priorEventMeta)

      actual shouldBe (CommandHandlerResponse())
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
      val actual: CommandHandlerResponse = cmdhandler.handleGetCommand(cmd, priorEventMeta)
      actual shouldBe (CommandHandlerResponse().withFailure(FailureResponse().withNotFound("entity not found")))
    }
  }
}
