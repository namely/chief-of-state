package com.namely.chiefofstate

import com.google.protobuf.any.Any
import com.google.protobuf.empty.Empty
import com.google.protobuf.timestamp.Timestamp
import com.namely.chiefofstate.test.helpers.TestSpec
import com.namely.protobuf.chiefofstate.v1.common.{MetaData => CosMetaData}
import io.superflat.lagompb.protobuf.v1.core.{MetaData => LagompbMetaData}

class UtilSpec extends TestSpec {

  "toCosMetaData" should {
    "return the right COS MetaData" in {
      val ts = Timestamp().withSeconds(3L).withNanos(2)
      val revisionNumber = 2
      val data = Map("foo" -> Any.pack(Empty.defaultInstance))

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
