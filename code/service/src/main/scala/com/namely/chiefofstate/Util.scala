package com.namely.chiefofstate

import java.time.{Instant, LocalDate, ZoneId}

import com.google.protobuf.ByteString
import com.google.protobuf.any.Any
import com.google.protobuf.timestamp.Timestamp
import com.namely.protobuf.chiefofstate.v1.internal.RemoteCommand
import com.namely.protobuf.chiefofstate.v1.internal.RemoteCommand.Header
import io.grpc.Metadata
import scala.collection.mutable

import scala.jdk.CollectionConverters.{CollectionHasAsScala, IterableHasAsScala}

object Util {
  implicit class Timestamps(timestamp: Timestamp) {

    /**
     * converts a protocol buffer timestamp to java Instant date
     *
     * @return java Instant date
     */
    def toInstant: Instant =
      Instant.ofEpochSecond(timestamp.seconds, timestamp.nanos)

    /**
     * converts a protocol buffer timestamp to java Instant date based upon a given time zone
     *
     * @param zoneId the time zone
     * @return java Instant date
     */
    def toInstant(zoneId: ZoneId): Instant =
      Instant
        .ofEpochSecond(timestamp.seconds, timestamp.nanos)
        .atZone(zoneId)
        .toInstant

    /**
     * converts a protocol buffer timestamp to java local date based upon a given time zone
     *
     * @param zoneId the time zone id
     * @return  java local date
     */
    def toLocalDate(zoneId: ZoneId): LocalDate =
      Instant
        .ofEpochSecond(timestamp.seconds, timestamp.nanos)
        .atZone(zoneId)
        .toLocalDate
  }

  implicit class Instants(instant: Instant) {

    /**
     * converts a java Instant date to a google protocol buffer timestamp
     *
     * @return Protobuf timestamp
     */
    def toTimestamp: Timestamp =
      Timestamp()
        .withNanos(instant.getNano)
        .withSeconds(instant.getEpochSecond)
  }

  /**
   * Extracts the proto message package name
   *
   * @param proto the protocol buffer message
   * @return the proto package name
   */
  def getProtoFullyQualifiedName(proto: Any): String = {
    proto.typeUrl.split('/').lastOption.getOrElse("")
  }

  /**
   * transforms a gRPC metadata to a RemoteCommand.Header
   *
   * @param headers the gRPC metadata
   * @return the list RemoteCommand.Header
   */
  def transformMetadataToRemoteCommandHeader(metadata: Metadata, keys: Seq[String]): Seq[Header] = {
    val output: mutable.ListBuffer[Header] = mutable.ListBuffer.empty[Header]

    keys.foreach(key => {
      if (key.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
        val bytesKey: Metadata.Key[Array[Byte]] = Metadata.Key.of(key, Metadata.BINARY_BYTE_MARSHALLER)
        val byteValues = metadata.getAll[Array[Byte]](bytesKey)

        if (byteValues != null) {
          byteValues.forEach(byteArray => {
            val byteString = ByteString.copyFrom(byteArray)
            output.append(Header(key = key).withBytesValue(byteString))
          })
        }
      } else {
        val stringKey: Metadata.Key[String] = Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER)
        val stringValues = metadata.getAll[String](stringKey)

        if (stringValues != null) {
          stringValues.forEach(stringValue => {
            output.append(Header(key = key).withStringValue(stringValue))
          })
        }
      }
    })

    output.toSeq
  }
}
