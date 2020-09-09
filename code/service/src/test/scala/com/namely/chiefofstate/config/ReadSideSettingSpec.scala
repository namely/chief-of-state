package com.namely.chiefofstate.config

import io.superflat.lagompb.testkit.BaseActorTestKit

class ReadSideSettingSpec extends BaseActorTestKit(s"""
    akka {
      actor {
        serialize-messages = on
        serializers {
          proto = "akka.remote.serialization.ProtobufSerializer"
         cmdSerializer = "io.superflat.lagompb.CommandSerializer"
        }
        serialization-bindings {
          "scalapb.GeneratedMessage" = proto
         "io.superflat.lagompb.Command" = cmdSerializer
        }
      }
    }
    """) {

  override def beforeEach(): Unit = {
    super.beforeEach()
    EnvironmentHelper.clearEnv()
  }

  "GrpcReadSideSetting" should {

    val settingName: String = "test"

    "handle getters and setters" in {
      val value: String = "foo"
      val config: ReadSideSetting = ReadSideSetting("test")

      // Gets unset key
      config.getSetting(settingName) shouldBe (None)

      // Adds key
      val addition: ReadSideSetting = config.addSetting(settingName, value)

      // Gets new key value
      addition.getSetting(settingName) shouldBe (Some(value))

      // Removes key
      val removed: ReadSideSetting = addition.removeSetting(settingName)

      // Gets removed key
      removed.getSetting(settingName) shouldBe (None)
    }

    "return all settings" in {
      val config: ReadSideSetting = ReadSideSetting("test")
        .addSetting("foo", "foo")
        .addSetting("bar", "bar")
        .addSetting("baz", "baz")

      config.listSettings should contain theSameElementsAs Map("foo" -> "foo", "bar" -> "bar", "baz" -> "baz")
    }

    "getReadSideSettings" should {

      "handle only read side configs" in {

        EnvironmentHelper.setEnv("control_config", "not-a-valid-config")
        EnvironmentHelper.setEnv("cos_read_side_config__host__rs0", "not-a-valid-config")
        EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__HOST__RS1", "host1")
        EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__PORT__RS1", "1")
        EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__GRPC_SOME_SETTING__RS1", "setting1")
        EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__HOST__RS2", "host2")
        EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__PORT__RS2", "2")
        EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__GRPC_SOME_SETTING__RS2", "setting2")

        val grpcReadSideSetting1: ReadSideSetting = ReadSideSetting("RS1", Some("host1"), Some(1))
          .addSetting("GRPC_SOME_SETTING", "setting1")

        val grpcReadSideSetting2: ReadSideSetting = ReadSideSetting("RS2", Some("host2"), Some(2))
          .addSetting("GRPC_SOME_SETTING", "setting2")

        val actual: Seq[ReadSideSetting] = ReadSideSetting.getReadSideSettings
        val expected: Seq[ReadSideSetting] = Seq(grpcReadSideSetting1, grpcReadSideSetting2)

        actual.length should be(expected.length)
        actual should contain theSameElementsAs (expected)
      }

      "throw an exception if one or more of the read side configurations is invalid" in {

        EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__HOST__", "not-a-valid-config")
        EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__PORT__", "0")

        val exception: Exception = intercept[Exception](ReadSideSetting.getReadSideSettings)
        exception.getMessage shouldBe ("One or more of the read side configurations is invalid")
      }

      "throw an exception if one or more of the read side configurations does not contain a host" in {
        EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__HOST__RS1", "host1")
        EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__PORT__RS1", "1")
        EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__PORT__RS2", "2")

        val exception: Exception = intercept[Exception](ReadSideSetting.getReadSideSettings)
        exception.getMessage shouldBe ("requirement failed: ProcessorId RS2 is missing a HOST")
      }

      "throw an exception if one or more of the read side configurations does not contain a port" in {
        EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__HOST__RS1", "host1")
        EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__PORT__RS1", "1")
        EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__HOST__RS2", "host2")

        val exception: Exception = intercept[Exception](ReadSideSetting.getReadSideSettings)
        exception.getMessage shouldBe ("requirement failed: ProcessorId RS2 is missing a PORT")
      }

      "throw an exception on an invalid setting name" in {
        EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__HOST__RS1", "host1")
        EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG__PORT__RS1", "1")
        EnvironmentHelper.setEnv("COS_READ_SIDE_CONFIG____RS1", "setting1")

        val exception: Exception = intercept[Exception](ReadSideSetting.getReadSideSettings)
        exception.getMessage shouldBe ("requirement failed: Setting must be defined in COS_READ_SIDE_CONFIG____RS1")
      }
    }
  }
}
