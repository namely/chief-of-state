package com.namely.chiefofstate

import com.google.protobuf.ByteString
import com.google.protobuf.any.Any
import com.google.protobuf.timestamp.Timestamp
import com.namely.protobuf.chiefofstate.v1.internal.RemoteCommand.Header
import io.grpc.{Metadata, Status, StatusException, StatusRuntimeException}

import java.time.{Instant, LocalDate, ZoneId}
import scala.collection.mutable
import scala.util.{Failure, Try}

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
   * @param metadata the gRPC metadata
   * @param keys the header keys to look for
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

  /**
   * helper to transform Try failures into status exceptions
   */
  def makeFailedStatusPf[U]: PartialFunction[Throwable, Try[U]] = {
    case e: Throwable => Failure(makeStatusException(e))
  }

  /**
   * helper method to transform throwables into StatusExceptions
   *
   * @param exception a throwable
   * @return that throwable as a StatusException
   */
  def makeStatusException(exception: Throwable): StatusException = {
    exception match {
      case e: StatusException =>
        e

      case e: StatusRuntimeException =>
        new StatusException(e.getStatus)

      case e: IllegalArgumentException =>
        val errMsg = e.getMessage.stripPrefix("requirement failed: ")
        new StatusException(Status.INVALID_ARGUMENT.withDescription(errMsg))

      case e: Throwable =>
        new StatusException(Status.INTERNAL.withDescription(e.getMessage()))
    }
  }

  /**
   * helper to convert io.grpc.Status to com.google.rpc.status.Status
   *
   * @param status a io.grpc.Status
   * @return a the rpc Status instance
   */
  def toRpcStatus(status: Status): com.google.rpc.status.Status = {
    com.google.rpc.status.Status(
      code = status.getCode.value,
      message = Option(status.getDescription).getOrElse("")
    )
  }

  /**
   * function to get the shard index given the entity id and number of shards
   *
   * @param entityId string entity ID
   * @param numShards number of shards
   * @return the tag
   */
  def getShardIndex(entityId: String, numShards: Int): Int = {
    Math.abs(entityId.hashCode) % numShards
  }
}
