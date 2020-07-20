package com.namely.chiefofstate

import akka.actor.typed.ActorSystem
import io.superflat.lagompb.testkit.{LagompbActorTestKit, LagompbSpec}

class GrpcReadSideConfigSpec extends LagompbActorTestKit(s"""
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
    """){

  "GrpcReadSideConfig" should {

    val settingName: String = "test"

    "handle getters and setters" in {
      val value: String = "foo"
      val config: GrpcReadSideConfig = GrpcReadSideConfig("test")

      // Gets unset key
      config.getSetting(settingName) shouldBe(None)

      // Adds key
      config.addSetting(settingName, value)

      // Gets new key value
      config.getSetting(settingName) shouldBe(Some(value))

      // Removes key
      config.removeSetting(settingName) shouldBe(true)

      // Gets removed key
      config.getSetting(settingName) shouldBe(None)

      // Removes non-existent key
      config.removeSetting("not-a-key") shouldBe(false)
    }

    "return all settings" in {
      val config: GrpcReadSideConfig = GrpcReadSideConfig("test")

      config.addSetting("foo", "foo")
      config.addSetting("bar", "bar")
      config.addSetting("baz", "baz")

      config.listSettings should contain theSameElementsAs Map(
        "foo" -> "foo",
        "bar" -> "bar",
        "baz" -> "baz"
      )
    }
  }
}
