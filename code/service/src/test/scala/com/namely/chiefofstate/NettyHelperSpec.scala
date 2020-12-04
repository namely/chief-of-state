package com.namely.chiefofstate

import com.namely.chiefofstate.helper.BaseSpec
import io.grpc.ManagedChannel

class NettyHelperSpec extends BaseSpec {
  "buildChannel" should {
    "create a plaintext channel" in {
      noException shouldBe thrownBy({NettyHelper.buildChannel("x", 1, false)})
    }
    "create a tls channel" in {
      noException shouldBe thrownBy({NettyHelper.buildChannel("x", 1, true)})
    }
  }
}
