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

class GrpcServiceImplSpec extends BaseSpec {
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
