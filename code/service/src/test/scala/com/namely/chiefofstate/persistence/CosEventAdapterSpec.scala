package com.namely.chiefofstate.persistence

import com.google.protobuf.any.Any
import com.google.protobuf.wrappers.StringValue
import com.namely.chiefofstate.test.helpers.TestSpec
import com.namely.protobuf.chiefofstate.v1.common.{MetaData => CosMetaData}
import com.namely.protobuf.chiefofstate.v1.persistence.EventWrapper
import io.superflat.lagompb.protobuf.v1.core.{MetaData => LagompbMetaData}
import io.superflat.lagompb.protobuf.v1.core.{EventWrapper => LagompbEventWrapper}

class CosEventAdapterSpec extends TestSpec {
  ".toJournal" should {
    "return a cos event wrapper" in {
      val event = Any.pack(StringValue("event"))
      val state = Any.pack(StringValue("state"))
      val revision = 2

      val before = LagompbEventWrapper()
        .withEvent(event)
        .withResultingState(state)
        .withMeta(LagompbMetaData().withRevisionNumber(revision))

      val expected = EventWrapper()
        .withEvent(event)
        .withResultingState(state)
        .withMeta(CosMetaData().withRevisionNumber(revision))

      val actual = CosEventAdapter.toJournal(before)

      actual shouldBe (expected)
    }
  }

  ".fromJournal" should {
    "return a lagom-pb event wrapper" in {
      val event = Any.pack(StringValue("event"))
      val state = Any.pack(StringValue("state"))
      val revision = 2

      val expected = LagompbEventWrapper()
        .withEvent(event)
        .withResultingState(state)
        .withMeta(LagompbMetaData().withRevisionNumber(revision))

      val before = EventWrapper()
        .withEvent(event)
        .withResultingState(state)
        .withMeta(CosMetaData().withRevisionNumber(revision))

      val actual = CosEventAdapter.fromJournal(before, "")
      actual.events.length shouldBe (1)
      actual.events.head shouldBe (expected)
    }
  }

  ".manifest" should {
    "yield the stable string" in {
      CosEventAdapter.manifest(null) shouldBe (CosEventAdapter.MANIFEST)
    }
  }
}
