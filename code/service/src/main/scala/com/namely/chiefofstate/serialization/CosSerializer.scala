/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.serialization

import akka.actor.ExtendedActorSystem
import akka.serialization.SerializerWithStringManifest
import scalapb.{GeneratedMessage, GeneratedMessageCompanion}
import com.namely.protobuf.chiefofstate.v1.internal.WireMessageWithActorRef
import akka.actor.typed.ActorRefResolver
import java.nio.charset.StandardCharsets
import com.google.protobuf.any
import com.google.protobuf.ByteString
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.adapter._

class CosSerializer(val system: ExtendedActorSystem) extends SerializerWithStringManifest {

  private lazy val actorRefResolver: ActorRefResolver = ActorRefResolver(system.toTyped)

  // build a reverse lookup of type url's to companions
  private[serialization] lazy val companionMap: Map[String, GeneratedMessageCompanion[_ <: GeneratedMessage]] =
    CosSerializer.companions
      .map(c => (CosSerializer.getTypeUrl(c) -> c))
      .toMap

  // returns the unique ID for this serializer defined in the companion
  override def identifier: Int = CosSerializer.IDENTIFIER

  /**
   * Given a scalapb generated message, return the type URL
   *
   * @param o a scalapb message as an any
   * @return string type url
   */
  override def manifest(o: AnyRef): String = {
    o match {
      case m: MessageWithActorRef =>
        CosSerializer.getTypeUrl(WireMessageWithActorRef)

      case e: GeneratedMessage if companionMap contains CosSerializer.getTypeUrl(e.companion) =>
        CosSerializer.getTypeUrl(e.companion)

      case default =>
        throw new IllegalArgumentException(s"cannot serialize type ${default.getClass().getName()}")
    }
  }

  /**
   * converts proto bytes to GeneratedMessage
   *
   * @param bytes proto bytes
   * @param manifest type URL
   * @return generated message
   */
  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
    val parsed: GeneratedMessage = companionMap.get(manifest) match {
      case None =>
        throw new IllegalArgumentException(s"unrecognized manifest, $manifest")

      case Some(companion) =>
        companion.parseFrom(bytes)
    }

    parsed match {
      case m: WireMessageWithActorRef =>
        val innerTypeUrl: String = CosSerializer.getTypeUrl(m.getMessage.typeUrl)
        val innerCompanion = companionMap.get(innerTypeUrl) match {
          case Some(companion) =>
            companion
          case None =>
            throw new IllegalArgumentException(s"unknown message type $innerTypeUrl")
        }

        val actorRefStr: String =
          new String(m.actorRef.toByteArray, StandardCharsets.UTF_8)

        val ref: ActorRef[GeneratedMessage] =
          actorRefResolver.resolveActorRef[GeneratedMessage](actorRefStr)

        MessageWithActorRef(
          message = m.getMessage.unpack(innerCompanion),
          actorRef = ref
        )

      case default: GeneratedMessage =>
        default.asInstanceOf[AnyRef]
    }
  }

  /**
   * converts generated message to proto bytes
   *
   * @param o a scalapb generated message
   * @return proto bytes
   */
  override def toBinary(o: AnyRef): Array[Byte] = {
    o match {
      case m: MessageWithActorRef =>
        val actorBytes: Array[Byte] = actorRefResolver
          .toSerializationFormat(m.actorRef)
          .getBytes(StandardCharsets.UTF_8)

        if (!companionMap.contains(CosSerializer.getTypeUrl(m.message.companion))) {
          throw new IllegalArgumentException(s"cannot serialize ${m.message.companion.scalaDescriptor.fullName}")
        }

        WireMessageWithActorRef(
          message = Some(any.Any.pack(m.message)),
          actorRef = ByteString.copyFrom(actorBytes)
        ).toByteArray

      case e: GeneratedMessage =>
        e.toByteArray

      case default =>
        throw new IllegalArgumentException(s"cannot serialize ${o.getClass().getName()}")
    }
  }
}

/**
 * Companion object for the serializer
 */
object CosSerializer {
  // unique ID for serializer
  private[serialization] val IDENTIFIER: Int = 5001

  // list of supported companions
  private[serialization] val companions: Seq[GeneratedMessageCompanion[_ <: GeneratedMessage]] =
    // recognizes all internal messages
    com.namely.protobuf.chiefofstate.v1.internal.InternalProto.messagesCompanions ++
    // recognizes all persistence messages
    com.namely.protobuf.chiefofstate.v1.persistence.PersistenceProto.messagesCompanions

  /**
   * returns at type URL given a companion
   *
   * @param companion scalapb generated message companion
   * @return the string type url
   */
  private[serialization] def getTypeUrl(companion: GeneratedMessageCompanion[_]): String = {
    getTypeUrl(companion.scalaDescriptor.fullName)
  }

  /**
   * returns a standardized type URL given a type url, which may have
   * a google prefix
   *
   * @param typeUrl a type url
   * @return standardized type url without google prefix
   */
  private[serialization] def getTypeUrl(typeUrl: String): String = {
    typeUrl.split("/").last
  }
}
