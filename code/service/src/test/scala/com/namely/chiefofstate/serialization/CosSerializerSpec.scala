/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.serialization

import akka.actor.ExtendedActorSystem
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.scaladsl.adapter._
import com.google.protobuf.any.Any
import com.google.protobuf.wrappers.StringValue
import com.namely.chiefofstate.helper.BaseActorSpec
import com.namely.protobuf.chiefofstate.v1.common.Header
import com.namely.protobuf.chiefofstate.v1.internal.{
  GetStateCommand,
  RemoteCommand,
  SendCommand,
  WireMessageWithActorRef
}
import com.namely.protobuf.chiefofstate.v1.tests.OpenAccount
import scalapb.GeneratedMessage

class CosSerializerSpec extends BaseActorSpec(s"""
    akka {
      actor {
        serialize-messages = on
        serializers {
          cosSerializer = "com.namely.chiefofstate.serialization.CosSerializer"
        }
        serialization-bindings {
          "scalapb.GeneratedMessage" = cosSerializer
          "com.namely.chiefofstate.serialization.ScalaMessage" = cosSerializer
        }
      }
    }
    """) {

  // create a shared extended system for use in constructors
  lazy val extendedSystem: ExtendedActorSystem = system.toClassic.asInstanceOf[ExtendedActorSystem]

  "Akka serialization" should {
    "serialize ChiefOfState command" in {
      val probe: TestProbe[GeneratedMessage] = createTestProbe[GeneratedMessage]()

      val remoteCommand = RemoteCommand()
        .withCommand(Any.pack(OpenAccount()))
        .addPropagatedHeaders(Header().withKey("header-1").withStringValue("header-value-1"))

      val sendCommand = SendCommand().withRemoteCommand(remoteCommand)

      val command: MessageWithActorRef = MessageWithActorRef(message = sendCommand, actorRef = probe.ref)

      serializationTestKit.verifySerialization(command)
    }
  }

  ".manifest" should {
    "recognize a MessageWithActorRef" in {
      val msg = MessageWithActorRef(SendCommand(), null)
      (new CosSerializer(extendedSystem)).manifest(msg).nonEmpty shouldBe true
    }
    "recognize a GeneratedMessage" in {
      val msg = SendCommand.defaultInstance
      (new CosSerializer(extendedSystem)).manifest(msg).nonEmpty shouldBe true
    }
    "fail on unrecognized messages" in {
      assertThrows[IllegalArgumentException] {
        (new CosSerializer(extendedSystem)).manifest(StringValue.defaultInstance)
      }
    }
  }

  ".fromBinary" should {
    "error for unrecognized type url" in {
      val serializer = new CosSerializer(extendedSystem)
      val err = intercept[IllegalArgumentException] {
        serializer.fromBinary(Array.emptyByteArray, "bad-url")
      }

      err.getMessage() shouldBe "unrecognized manifest, bad-url"
    }
  }
  "MessageWithActorRef" should {
    "successfully serialize and deserialize" in {
      val probe: TestProbe[GeneratedMessage] = createTestProbe[GeneratedMessage]()
      val serializer = new CosSerializer(extendedSystem)
      val innerMsg = SendCommand().withGetStateCommand(GetStateCommand().withEntityId("x"))
      val outerMsg = MessageWithActorRef(innerMsg, probe.ref)
      // serialize the message
      val serialized: Array[Byte] = serializer.toBinary(outerMsg)
      // prove you can deserialize the byte array
      noException shouldBe thrownBy(WireMessageWithActorRef.parseFrom(serialized))
      // deserialize it
      val manifest = serializer.manifest(outerMsg)
      val actual = serializer.fromBinary(serialized, manifest)
      actual.isInstanceOf[MessageWithActorRef] shouldBe true
      // assert unchanged
      actual.asInstanceOf[MessageWithActorRef] shouldBe outerMsg
    }
    "fail to serialize with unknown child message" in {
      val probe: TestProbe[GeneratedMessage] = createTestProbe[GeneratedMessage]()
      val serializer = new CosSerializer(extendedSystem)
      // construct a message with an unregistered type
      val outerMsg = MessageWithActorRef(StringValue("x"), probe.ref)
      // serialize the message
      val err = intercept[IllegalArgumentException] {
        serializer.toBinary(outerMsg)
      }
      err.getMessage().startsWith("cannot serialize") shouldBe true
    }
    "fail to deserialize with unknown child message" in {
      val msg = WireMessageWithActorRef().withMessage(Any.pack(StringValue("x")))

      val manifest = WireMessageWithActorRef.scalaDescriptor.fullName

      val serializer = new CosSerializer(extendedSystem)
      val err = intercept[IllegalArgumentException] {
        serializer.fromBinary(msg.toByteArray, manifest)
      }

      err.getMessage().startsWith("unknown message type") shouldBe true
    }
  }
  "scalapb GeneratedMessages" should {
    "successfully serialize and deserialize" in {
      val serializer = new CosSerializer(extendedSystem)
      val msg = SendCommand().withGetStateCommand(GetStateCommand().withEntityId("x"))
      val serialized: Array[Byte] = serializer.toBinary(msg)
      // check proto serialization
      noException shouldBe thrownBy { SendCommand.parseFrom(serialized) }
      // deserialize
      serializer.fromBinary(serialized, serializer.manifest(msg)) shouldBe msg
    }
    "fail to deserialize unknown messages" in {
      val msg = StringValue("x")
      val manifest = msg.companion.scalaDescriptor.fullName
      val serializer = new CosSerializer(extendedSystem)
      val err = intercept[IllegalArgumentException] {
        serializer.fromBinary(msg.toByteArray, manifest)
      }

      err.getMessage().startsWith("unrecognized manifest") shouldBe true
    }
  }
}
