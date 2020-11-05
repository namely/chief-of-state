package com.namely.chiefofstate.interceptors

import java.time.{Instant, ZoneId}

import com.google.protobuf.timestamp.Timestamp
import com.namely.chiefofstate.Util.{Instants, Timestamps}
import com.namely.chiefofstate.helper.BaseSpec
import com.namely.protobuf.chiefofstate.v1.tests.AccountOpened
import io.grpc.{Metadata, Status, StatusException, StatusRuntimeException}

import scala.util.Failure

class B3PropagationSpec extends BaseSpec {
  ".spanFromHeaders" should {
    "create a span from gRPC metadata" in { fail("not implemented") }
    "handles all missing keys" in { fail("not implemented") }
  }

  ".getSamplingDecision" should {
    "sample during debug" in { fail("not implemented") }
    "handle sampled" in { fail("not implemented") }
    "handle not sampled" in { fail("not implemented") }
    "handle unknown decision" in { fail("not implemented") }
  }

  ".updateHeadersWithSpan" should {
    "add trace and span headers" in { fail("not implemented") }
    "handle a null parent span" in { fail("not implemented") }
  }

  ".encodeSamplingDecision" should {
    "handle sample" in { fail("not implemented") }
    "handle doNotSample" in { fail("not implemented") }
    "handle unknown sampling decision" in { fail("not implemented") }
  }

}
