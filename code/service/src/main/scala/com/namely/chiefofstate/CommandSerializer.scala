/*
 * MIT License
 *
 * Copyright (c) 2020 Namely
 */

package com.namely.chiefofstate

import akka.actor.ExtendedActorSystem
import akka.actor.typed.{ActorRef, ActorRefResolver}
import akka.actor.typed.scaladsl.adapter._
import akka.serialization.SerializerWithStringManifest
import com.namely.protobuf.chiefofstate.v1.internal.{CommandReply, CommandWrapper, SendCommand}

import java.nio.charset.StandardCharsets

sealed class CommandSerializer(val system: ExtendedActorSystem) extends SerializerWithStringManifest {

  private val actorRefResolver: ActorRefResolver = ActorRefResolver(system.toTyped)
  final private val commandManifest: String = classOf[AggregateCommand].getName

  override def identifier: Int = 5000

  override def manifest(o: AnyRef): String = o.getClass.getName

  override def toBinary(o: AnyRef): Array[Byte] = {
    o match {
      case AggregateCommand(cmd, actorRef, data) =>
        val actorBytes: Array[Byte] = actorRefResolver
          .toSerializationFormat(actorRef)
          .getBytes(StandardCharsets.UTF_8)

        CommandWrapper()
          .withCommand(com.google.protobuf.any.Any.pack(cmd))
          .withActorRef(com.google.protobuf.ByteString.copyFrom(actorBytes))
          .withData(data)
          .toByteArray

      case _ => throw new RuntimeException("No Command Provided...")
    }
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    manifest match {
      case `commandManifest` =>
        val wrapper: CommandWrapper = CommandWrapper.parseFrom(bytes)

        val actorRefStr: String =
          new String(wrapper.actorRef.toByteArray, StandardCharsets.UTF_8)

        val ref: ActorRef[CommandReply] =
          actorRefResolver.resolveActorRef[CommandReply](actorRefStr)

        AggregateCommand(wrapper.getCommand.unpack[SendCommand], ref, wrapper.data)

      case _ => throw new RuntimeException("Wrong Command manifest....")
    }
  }
}
