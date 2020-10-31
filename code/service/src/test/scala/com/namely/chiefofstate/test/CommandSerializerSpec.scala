package com.namely.chiefofstate.test

import akka.actor.testkit.typed.scaladsl.TestProbe
import com.google.protobuf.any.Any
import com.namely.chiefofstate.test.helper.BaseActorSpec
import com.namely.chiefofstate.AggregateCommand
import com.namely.protobuf.chiefofstate.v1.internal.{CommandReply, HandleCommand, SendCommand}
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
      val command: AggregateCommand = AggregateCommand(
        SendCommand()
          .withHandleCommand(
            HandleCommand()
              .withCommand(Any.pack(OpenAccount()))
              .withEntityId("123")
          ),
        probe.ref,
        Map.empty[String, Any]
      )

      serializationTestKit.verifySerialization(command)
    }
  }
}
