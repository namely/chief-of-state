/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.readside

import com.namely.protobuf.chiefofstate.v1.common.MetaData
import com.namely.protobuf.chiefofstate.v1.readside.{ HandleReadSideRequest, HandleReadSideResponse }
import com.namely.protobuf.chiefofstate.v1.readside.ReadSideHandlerServiceGrpc.ReadSideHandlerServiceBlockingStub
import io.grpc.Metadata
import io.grpc.stub.MetadataUtils
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import org.slf4j.{ Logger, LoggerFactory }

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.{ Failure, Success, Try }

/**
 * read side processor that sends messages to a gRPC server that implements
 * the ReadSideHandler service
 *
 * @param processorId the unique Id for this read side
 * @param readSideHandlerServiceBlockingStub a blocking client for a ReadSideHandler
 */
private[readside] class ReadSideHandlerImpl(
    processorId: String,
    readSideHandlerServiceBlockingStub: ReadSideHandlerServiceBlockingStub)(implicit val ec: ExecutionContext)
    extends ReadSideHandler {

  private val COS_EVENT_TAG_HEADER = "x-cos-event-tag"
  private val COS_ENTITY_ID_HEADER = "x-cos-entity-id"
  private[readside] val spanName: String = "ReadSideHandler.processEvent"

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  /**
   * Processes events read from the Journal
   *
   * @param event          the actual event
   * @param eventTag       the event tag
   * @param resultingState the resulting state of the applied event
   * @param meta           the additional meta data
   * @return an eventual HandleReadSideResponse
   */
  override def doProcessEvent(
      event: com.google.protobuf.any.Any,
      eventTag: String,
      resultingState: com.google.protobuf.any.Any,
      meta: MetaData): Boolean = {

    // start the span
    val span: Try[Span] = Try {
      GlobalOpenTelemetry
        .getTracer(getClass.getPackage().getName())
        .spanBuilder(spanName)
        .setAttribute("component", this.getClass.getName)
        .startSpan()
    }

    val scope = span.map(_.makeCurrent())

    val response: Try[HandleReadSideResponse] = Try {
      val headers = new Metadata()
      headers.put(Metadata.Key.of(COS_ENTITY_ID_HEADER, Metadata.ASCII_STRING_MARSHALLER), meta.entityId)
      headers.put(Metadata.Key.of(COS_EVENT_TAG_HEADER, Metadata.ASCII_STRING_MARSHALLER), eventTag)

      MetadataUtils
        .attachHeaders(readSideHandlerServiceBlockingStub, headers)
        .handleReadSide(HandleReadSideRequest().withEvent(event).withState(resultingState).withMeta(meta))
    }

    // finish the span
    scope.foreach(_.close())
    span.foreach(_.end())

    // return the response
    response match {
      // return true when the remote server responds with "true"
      case Success(value) if value.successful =>
        logger.debug(s"success for id=${meta.entityId}, revisionNumber=${meta.revisionNumber}")
        true

      // return false when remote server responds with "false"
      case Success(value) =>
        val errMsg: String =
          s"read side returned failure, processor=${processorId}, id=${meta.entityId}, revisionNumber=${meta.revisionNumber}"
        logger.warn(errMsg)
        false

      // return false when remote server fails
      case Failure(exception) =>
        logger.error(
          s"read side processing failure, processor=${processorId}, id=${meta.entityId}, revisionNumber=${meta.revisionNumber}, cause=${exception
            .getMessage()}")
        // for debug purposes, log the stack trace as well
        logger.debug("remote handler failure", exception)
        false
    }
    response.map(_.successful).getOrElse(false)
  }
}

private[readside] trait ReadSideHandler {

  implicit val ec: ExecutionContext

  /**
   * Processes events read from the Journal.
   * Exponentially backoff with a 10& gain modifier until it reaches the upper threshold of maxBackoffSeconds.
   *
   * @param event               the actual event
   * @param eventTag            the event tag
   * @param resultingState      the resulting state of the applied event
   * @param meta                the additional meta data
   * @param policy              the retry policy. If provided, overrides the default backoff policy. Testing method
   * @param minBackoffSeconds   the minimum number of seconds to backoff
   * @param maxBackoffSeconds   the maximum number of seconds to backoff
   * @return                    Future[Boolean] for success
   */
  def processEvent(
      event: com.google.protobuf.any.Any,
      eventTag: String,
      resultingState: com.google.protobuf.any.Any,
      meta: MetaData,
      policy: Option[retry.Policy] = Some(retry.Directly()),
      minBackoffSeconds: Long = 1L,
      maxBackoffSeconds: Long = 30L): Future[Boolean] = {

    implicit val success: retry.Success[Boolean] = retry.Success(x => x)

    val finalPolicy: retry.Policy =
      policy.getOrElse(retry.Backoff(maxBackoffSeconds.toInt, FiniteDuration(minBackoffSeconds, TimeUnit.SECONDS)))

    val f: Future[Boolean] = finalPolicy.apply(() =>
      Future {
        doProcessEvent(event, eventTag, resultingState, meta)
      })

    f
  }

  /**
   * Processes events read from the Journal.
   *
   * @param event               the actual event
   * @param eventTag            the event tag
   * @param resultingState      the resulting state of the applied event
   * @param meta                the additional meta data
   * @return                    Boolean for success
   */
  protected def doProcessEvent(
      event: com.google.protobuf.any.Any,
      eventTag: String,
      resultingState: com.google.protobuf.any.Any,
      meta: MetaData): Boolean
}
