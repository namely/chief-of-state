package com.namely.chiefofstate

import io.superflat.lagompb.testkit.LagompbSpec

class UtilSpec extends LagompbSpec {

  override def beforeEach(): Unit = {
    super.beforeEach()
    EnvironmentHelper.clearEnv()
  }

  "Chief-Of-State Helper" should {

    "getReadSideConfigs" should {

      "handle only read side configs" in {

        EnvironmentHelper.setEnv("control_config", "not-a-valid-config")
        EnvironmentHelper.setEnv("cos_readside_config__host__rs0", "not-a-valid-config")
        EnvironmentHelper.setEnv("COS_READSIDE_CONFIG__HOST__RS1", "host1")
        EnvironmentHelper.setEnv("COS_READSIDE_CONFIG__PORT__RS1", "1")
        EnvironmentHelper.setEnv("COS_READSIDE_CONFIG__GRPC_SOME_SETTING__RS1", "setting1")
        EnvironmentHelper.setEnv("COS_READSIDE_CONFIG__HOST__RS2", "host2")
        EnvironmentHelper.setEnv("COS_READSIDE_CONFIG__PORT__RS2", "2")
        EnvironmentHelper.setEnv("COS_READSIDE_CONFIG__GRPC_SOME_SETTING__RS2", "setting2")

        val grpcReadSideConfig1: ReadSideConfig = ReadSideConfig("RS1", Some("host1"), Some(1))
          .addSetting("GRPC_SOME_SETTING", "setting1")

        val grpcReadSideConfig2: ReadSideConfig = ReadSideConfig("RS2", Some("host2"), Some(2))
          .addSetting("GRPC_SOME_SETTING", "setting2")

        val actual: Seq[ReadSideConfig] = Util.getReadSideConfigs
        val expected: Seq[ReadSideConfig] = Seq(grpcReadSideConfig1, grpcReadSideConfig2)

        actual.length should be(expected.length)
        actual should contain theSameElementsAs (expected)
      }

      "throw an exception if one or more of the read side configurations is invalid" in {

        EnvironmentHelper.setEnv("COS_READSIDE_CONFIG__HOST__", "not-a-valid-config")
        EnvironmentHelper.setEnv("COS_READSIDE_CONFIG__PORT__", "0")

        val exception: Exception = intercept[Exception](Util.getReadSideConfigs)
        exception.getMessage shouldBe ("One or more of the read side configurations is invalid")
      }

      "throw an exception if one or more of the read side configurations does not contain a host" in {
        EnvironmentHelper.setEnv("COS_READSIDE_CONFIG__HOST__RS1", "host1")
        EnvironmentHelper.setEnv("COS_READSIDE_CONFIG__PORT__RS1", "1")
        EnvironmentHelper.setEnv("COS_READSIDE_CONFIG__PORT__RS2", "2")

        val exception: Exception = intercept[Exception](Util.getReadSideConfigs)
        exception.getMessage shouldBe ("requirement failed: ProcessorId RS2 is missing a HOST")
      }

      "throw an exception if one or more of the read side configurations does not contain a port" in {
        EnvironmentHelper.setEnv("COS_READSIDE_CONFIG__HOST__RS1", "host1")
        EnvironmentHelper.setEnv("COS_READSIDE_CONFIG__PORT__RS1", "1")
        EnvironmentHelper.setEnv("COS_READSIDE_CONFIG__HOST__RS2", "host2")

        val exception: Exception = intercept[Exception](Util.getReadSideConfigs)
        exception.getMessage shouldBe ("requirement failed: ProcessorId RS2 is missing a PORT")
      }

      "throw an exception on an invalid setting name" in {
        EnvironmentHelper.setEnv("COS_READSIDE_CONFIG__HOST__RS1", "host1")
        EnvironmentHelper.setEnv("COS_READSIDE_CONFIG__PORT__RS1", "1")
        EnvironmentHelper.setEnv("COS_READSIDE_CONFIG____RS1", "setting1")

        val exception: Exception = intercept[Exception](Util.getReadSideConfigs)
        exception.getMessage shouldBe ("requirement failed: Setting must be defined in COS_READSIDE_CONFIG____RS1")
      }
    }
  }
}

object EnvironmentHelper {

  def setEnv(key: String, value: String): Unit = {
    val field = System.getenv().getClass.getDeclaredField("m")
    field.setAccessible(true)
    val map: java.util.Map[java.lang.String, java.lang.String] =
      field.get(System.getenv()).asInstanceOf[java.util.Map[java.lang.String, java.lang.String]]
    map.put(key, value)
  }

  def clearEnv(): Unit = {
    val field = System.getenv().getClass.getDeclaredField("m")
    field.setAccessible(true)
    val map: java.util.Map[java.lang.String, java.lang.String] =
      field.get(System.getenv()).asInstanceOf[java.util.Map[java.lang.String, java.lang.String]]
    map.clear()
  }
}
