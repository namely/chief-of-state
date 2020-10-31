package com.namely.chiefofstate.test.config

import com.namely.chiefofstate.config.{ReadSideConfig, ReadSideConfigReader}
import com.namely.chiefofstate.test.helper.{BaseSpec, EnvironmentHelper}

class ReadSideConfigReaderSpec extends BaseSpec {

  override def beforeEach(): Unit = {
    super.beforeEach()
    EnvironmentHelper.clearEnv()
  }

  "ReadSideConfigFactory" should {
    "help load all read side config" in {
      EnvironmentHelper.setEnv("control_config", "not-a-valid-config")
      EnvironmentHelper.setEnv("cos_read_side_config__host__rs0", "not-a-valid-config")
      EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__HOST__RS1", "host1")
      EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__PORT__RS1", "1")
      EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__GRPC_SOME_SETTING__RS1", "setting1")
      EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__HOST__RS2", "host2")
      EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__PORT__RS2", "2")
      EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__GRPC_SOME_SETTING__RS2", "setting2")

      val grpcReadSideSetting1: ReadSideConfig = ReadSideConfig("RS1", Some("host1"), Some(1))
        .addSetting("GRPC_SOME_SETTING", "setting1")

      val grpcReadSideSetting2: ReadSideConfig = ReadSideConfig("RS2", Some("host2"), Some(2))
        .addSetting("GRPC_SOME_SETTING", "setting2")

      val actual: Seq[ReadSideConfig] = ReadSideConfigReader.getReadSideSettings
      val expected: Seq[ReadSideConfig] = Seq(grpcReadSideSetting1, grpcReadSideSetting2)

      actual.length should be(expected.length)
      actual should contain theSameElementsAs (expected)
    }

    "throw an exception if one or more of the read side configurations is invalid" in {
      EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__HOST__", "not-a-valid-config")
      EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__PORT__", "0")

      val exception: Exception = intercept[Exception](ReadSideConfigReader.getReadSideSettings)
      exception.getMessage shouldBe ("One or more of the read side configurations is invalid")
    }

    "throw an exception if one or more of the read side configurations does not contain a host" in {
      EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__HOST__RS1", "host1")
      EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__PORT__RS1", "1")
      EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__PORT__RS2", "2")

      val exception: Exception = intercept[Exception](ReadSideConfigReader.getReadSideSettings)
      exception.getMessage shouldBe ("requirement failed: ProcessorId RS2 is missing a HOST")
    }

    "throw an exception if one or more of the read side configurations does not contain a port" in {
      EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__HOST__RS1", "host1")
      EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__PORT__RS1", "1")
      EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__HOST__RS2", "host2")

      val exception: Exception = intercept[Exception](ReadSideConfigReader.getReadSideSettings)
      exception.getMessage shouldBe ("requirement failed: ProcessorId RS2 is missing a PORT")
    }

    "throw an exception on an invalid setting name" in {
      EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__HOST__RS1", "host1")
      EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__PORT__RS1", "1")
      EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG____RS1", "setting1")

      val exception: Exception = intercept[Exception](ReadSideConfigReader.getReadSideSettings)
      exception.getMessage shouldBe ("requirement failed: Setting must be defined in COS_READ_SIDE_CONFIG____RS1")
    }
  }
}
