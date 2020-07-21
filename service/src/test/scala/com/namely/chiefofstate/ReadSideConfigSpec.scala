package com.namely.chiefofstate

import io.superflat.lagompb.testkit.LagompbActorTestKit

class ReadSideConfigSpec extends LagompbActorTestKit(s"""
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

  "GrpcReadSideConfig" should {

    val settingName: String = "test"

    "handle getters and setters" in {
      val value: String = "foo"
      val config: ReadSideConfig = ReadSideConfig("test")

      // Gets unset key
      config.getSetting(settingName) shouldBe (None)

      // Adds key
      val addition: ReadSideConfig = config.addSetting(settingName, value)

      // Gets new key value
      addition.getSetting(settingName) shouldBe (Some(value))

      // Removes key
      val removed: ReadSideConfig = addition.removeSetting(settingName)

      // Gets removed key
      removed.getSetting(settingName) shouldBe (None)
    }

    "return all settings" in {
      val config: ReadSideConfig = ReadSideConfig("test")
        .addSetting("foo", "foo")
        .addSetting("bar", "bar")
        .addSetting("baz", "baz")

      config.listSettings should contain theSameElementsAs Map("foo" -> "foo", "bar" -> "bar", "baz" -> "baz")
    }
  }
}
