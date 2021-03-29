/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.readside

import com.namely.protobuf.chiefofstate.v1.common.MetaData
import com.namely.protobuf.chiefofstate.v1.readside.{HandleReadSideRequest, HandleReadSideResponse}
import com.namely.protobuf.chiefofstate.v1.readside.ReadSideHandlerServiceGrpc.ReadSideHandlerServiceBlockingStub
import io.grpc.Metadata
import io.grpc.stub.MetadataUtils
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import scala.util.Try

/**
 * read side processor that sends messages to a gRPC server that implements
 * the ReadSideHandler service
 *
 * @param readSideHandlerServiceBlockingStub a blocking client for a ReadSideHandler
 */
private[readside] class RemoteReadSideProcessor(
  readSideHandlerServiceBlockingStub: ReadSideHandlerServiceBlockingStub
) {
  private val COS_EVENT_TAG_HEADER = "x-cos-event-tag"
  private val COS_ENTITY_ID_HEADER = "x-cos-entity-id"

  /**
   * Processes events read from the Journal
   *
   * @param event          the actual event
   * @param eventTag       the event tag
   * @param resultingState the resulting state of the applied event
   * @param meta           the additional meta data
   * @return an eventual HandleReadSideResponse
   */
  def processEvent(
    event: com.google.protobuf.any.Any,
    eventTag: String,
    resultingState: com.google.protobuf.any.Any,
    meta: MetaData
  ): Try[HandleReadSideResponse] = {
    Try {
      // start the span
      val span: Span = GlobalOpenTelemetry
        .getTracer(getClass.getPackage().getName())
        .spanBuilder("RemoteReadSideProcessor.processEvent")
        .setAttribute("component", this.getClass.getName)
        .startSpan()

      val scope = span.makeCurrent()

      val headers = new Metadata()
      headers.put(Metadata.Key.of(COS_ENTITY_ID_HEADER, Metadata.ASCII_STRING_MARSHALLER), meta.entityId)
      headers.put(Metadata.Key.of(COS_EVENT_TAG_HEADER, Metadata.ASCII_STRING_MARSHALLER), eventTag)

      val response = MetadataUtils
        .attachHeaders(readSideHandlerServiceBlockingStub, headers)
        .handleReadSide(
          HandleReadSideRequest()
            .withEvent(event)
            .withState(resultingState)
            .withMeta(meta)
        )

      // finish the span
      scope.close()
      span.end()

      // return the response
      response
    }
  }
}
