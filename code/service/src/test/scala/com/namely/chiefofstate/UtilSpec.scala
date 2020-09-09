package com.namely.chiefofstate

import com.google.protobuf.timestamp.Timestamp
import com.namely.protobuf.chiefofstate.v1.common.{MetaData => CosMetaData}
import io.superflat.lagompb.protobuf.v1.core.{MetaData => LagompbMetaData}
import io.superflat.lagompb.testkit.BaseSpec

class UtilSpec extends BaseSpec {

  "toCosMetaData" should {
    "return the right COS MetaData" in {
      val ts = Timestamp().withSeconds(3L).withNanos(2)
      val revisionNumber = 2
      val data = Map("foo" -> "bar")

      val lagomMetaData = LagompbMetaData()
        .withRevisionNumber(revisionNumber)
        .withRevisionDate(ts)
        .withData(data)

      val expected = CosMetaData()
        .withRevisionNumber(revisionNumber)
        .withRevisionDate(ts)
        .withData(data)

      val actual = Util.toCosMetaData(lagomMetaData)

      actual shouldBe (expected)
    }
  }
}
