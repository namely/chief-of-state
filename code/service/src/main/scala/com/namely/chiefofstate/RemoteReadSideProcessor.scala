/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

import com.namely.protobuf.chiefofstate.v1.common.MetaData
import com.namely.protobuf.chiefofstate.v1.readside.{HandleReadSideRequest, HandleReadSideResponse}
import com.namely.protobuf.chiefofstate.v1.readside.ReadSideHandlerServiceGrpc.ReadSideHandlerServiceBlockingStub
import io.grpc.Metadata
import io.grpc.stub.MetadataUtils
import io.opentracing.Span
import io.opentracing.tag.Tags
import io.opentracing.util.GlobalTracer

import scala.util.Try

class RemoteReadSideProcessor(readSideHandlerServiceBlockingStub: ReadSideHandlerServiceBlockingStub) {
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
      val span: Span = GlobalTracer.get
        .buildSpan("RemoteReadSideProcessor.processEvent")
        .withTag(Tags.COMPONENT.getKey(), this.getClass().getName)
        .start()

      GlobalTracer.get.activateSpan(span)

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
      span.finish()

      // return the response
      response
    }
  }
}
