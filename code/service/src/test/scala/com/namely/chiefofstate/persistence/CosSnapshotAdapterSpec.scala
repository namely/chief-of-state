package com.namely.chiefofstate.persistence

import com.google.protobuf.any.Any
import com.google.protobuf.wrappers.StringValue
import com.google.protobuf.timestamp.Timestamp
import com.namely.chiefofstate.test.helpers.TestSpec
import com.namely.chiefofstate.Util
import com.namely.protobuf.chiefofstate.v1.common.{MetaData => CosMetaData}
import com.namely.protobuf.chiefofstate.v1.persistence.StateWrapper
import io.superflat.lagompb.protobuf.v1.core.{MetaData => LagompbMetaData}
import io.superflat.lagompb.protobuf.v1.core.{StateWrapper => LagompbStateWrapper}

class CosSnapshotAdapterSpec extends TestSpec {
  ".toJournal" should {
    "return a cos state wrapper" in {
      val state = Any.pack(StringValue("state"))
      val revision: Int = 2

      val before = LagompbStateWrapper()
        .withState(state)
        .withMeta(LagompbMetaData().withRevisionNumber(revision))

      val expected = StateWrapper()
        .withState(state)
        .withMeta(Util.toCosMetaData(before.getMeta))

      val actual: StateWrapper = CosSnapshotAdapter
        .toJournal(before)
        .asInstanceOf[StateWrapper]

      actual shouldBe (expected)
    }
  }

  ".fromJournal" should {
    "return a lagom-pb state wrapper" in {
      val state = Any.pack(StringValue("state"))
      val revision: Int = 2

      val before = StateWrapper()
        .withState(state)
        .withMeta(CosMetaData().withRevisionNumber(revision))

      val expected = LagompbStateWrapper()
        .withState(state)
        .withMeta(Util.toLagompbMetaData(before.getMeta))

      val actual: LagompbStateWrapper = CosSnapshotAdapter
        .fromJournal(before)
        .asInstanceOf[LagompbStateWrapper]

      actual shouldBe (expected)
    }
    "fail on unknown snapshot type" in {
      val failure = intercept[Exception] {
        CosSnapshotAdapter.fromJournal(StringValue("bad state"))
      }

      failure.getMessage().startsWith("snapshot adapter cannot unpack state of type") shouldBe (true)
    }
  }
}
