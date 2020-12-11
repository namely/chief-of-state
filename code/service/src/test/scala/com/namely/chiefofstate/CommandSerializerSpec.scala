/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

import akka.actor.testkit.typed.scaladsl.TestProbe
import com.google.protobuf.any.Any
import com.namely.chiefofstate.helper.BaseActorSpec
import com.namely.protobuf.chiefofstate.v1.internal.{CommandReply, RemoteCommand, SendCommand}
import com.namely.protobuf.chiefofstate.v1.tests.OpenAccount

class CommandSerializerSpec
    extends BaseActorSpec(
      s"""
    akka {
      actor {
        serialize-messages = on
        serializers {
          proto = "akka.remote.serialization.ProtobufSerializer"
          cmdSerializer = "com.namely.chiefofstate.CommandSerializer"
        }
        serialization-bindings {
          "scalapb.GeneratedMessage" = proto
          "com.namely.chiefofstate.AggregateCommand" = cmdSerializer
        }
      }
    }
    """
    ) {

  "Akka serialization" should {
    "serialize ChiefOfState command" in {
      val probe: TestProbe[CommandReply] = createTestProbe[CommandReply]()

      val remoteCommand = RemoteCommand()
        .withCommand(Any.pack(OpenAccount()))
        .addHeaders(RemoteCommand.Header().withKey("header-1").withStringValue("header-value-1"))

      val command: AggregateCommand = AggregateCommand(
        SendCommand().withRemoteCommand(remoteCommand),
        probe.ref,
        Map.empty[String, Any]
      )

      serializationTestKit.verifySerialization(command)
    }
  }
}
