/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

import com.namely.chiefofstate.helper.BaseSpec
import com.namely.protobuf.chiefofstate.v1.internal.CommandReply
import io.grpc.StatusException
import com.namely.protobuf.chiefofstate.v1.persistence.StateWrapper
import com.namely.protobuf.chiefofstate.v1.common.MetaData
import com.google.protobuf.any
import scala.util.Success
import com.google.rpc.error_details.BadRequest
import io.grpc.protobuf.StatusProto
import com.google.rpc.status.Status
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import io.grpc.Metadata
import com.namely.chiefofstate.config.WriteSideConfig
import com.namely.protobuf.chiefofstate.v1.service.ProcessCommandRequest
import com.google.protobuf.wrappers.StringValue
import com.namely.protobuf.chiefofstate.v1.internal.RemoteCommand

class GrpcServiceImplSpec extends BaseSpec {
  ".getRemoteCommand" should {
    "invoke the Util helper" in {

      val key: String = "some-header"
      val value: String = "some value"

      val config: WriteSideConfig = WriteSideConfig(
        host = "x",
        port = 0,
        useTls = false,
        enableProtoValidation = false,
        eventsProtos = Seq.empty[String],
        statesProtos = Seq.empty[String],
        propagatedHeaders = Seq(key)
      )

      val metadata: Metadata = new Metadata()
      val stringHeaderKey: Metadata.Key[String] = Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER)
      metadata.put(stringHeaderKey, value)

      val command = ProcessCommandRequest()
        .withCommand(any.Any.pack(StringValue("x")))

      val actual = GrpcServiceImpl.getRemoteCommand(config, command, metadata)

      val expected = RemoteCommand()
        .withCommand(command.getCommand)
        .addHeaders(
          RemoteCommand
            .Header()
            .withKey(key)
            .withStringValue(value)
        )

      actual shouldBe expected
    }
  }

  ".requireEntityId" should {
    "fail if entity missing" in {
      assertThrows[StatusException] {
        Await.result(GrpcServiceImpl.requireEntityId(""), Duration.Inf)
      }
    }
    "pass if entity provided" in {
      noException shouldBe thrownBy {
        Await.result(GrpcServiceImpl.requireEntityId("x"), Duration.Inf)
      }
    }
  }

  ".handleCommandReply" should {
    "pass through success" in {
      val stateWrapper = StateWrapper()
        .withMeta(MetaData().withRevisionNumber(2))

      val commandReply: CommandReply = CommandReply()
        .withState(stateWrapper)

      val actual = GrpcServiceImpl.handleCommandReply(commandReply)

      actual shouldBe Success(stateWrapper)
    }
    "preserve error details" in {
      // define a field violation
      val errField = BadRequest
        .FieldViolation()
        .withField("some_field")
        .withDescription("oh no")

      // create the bad request detail
      val errDetail: BadRequest = BadRequest()
        .addFieldViolations(errField)

      // create an error status with this detail
      val expectedStatus: com.google.rpc.status.Status =
        com.google.rpc.status
          .Status()
          .withCode(com.google.rpc.code.Code.INVALID_ARGUMENT.value)
          .withMessage("some error message")
          .addDetails(com.google.protobuf.any.Any.pack(errDetail))

      val commandReply: CommandReply = CommandReply()
        .withError(expectedStatus)

      val statusException: StatusException = intercept[StatusException] {
        GrpcServiceImpl.handleCommandReply(commandReply).get
      }

      val javaStatus = StatusProto.fromStatusAndTrailers(
        statusException.getStatus(),
        statusException.getTrailers()
      )

      val actual = Status.parseFrom(javaStatus.toByteArray())

      actual shouldBe expectedStatus

    }
    "handle defailt case" in {
      val commandReply: CommandReply = CommandReply()
        .withReply(CommandReply.Reply.Empty)

      assertThrows[StatusException] {
        GrpcServiceImpl.handleCommandReply(commandReply).get
      }
    }
  }
}
