/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.readside

import akka.projection.jdbc.scaladsl.JdbcHandler
import akka.projection.eventsourced.EventEnvelope
import com.namely.protobuf.chiefofstate.v1.persistence.EventWrapper
import akka.projection.jdbc.JdbcSession
import com.google.protobuf.any.{Any => ProtoAny}
import scala.util.Success
import scala.util.Try
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.util.Failure
import com.namely.protobuf.chiefofstate.v1.common.MetaData
import com.namely.protobuf.chiefofstate.v1.readside.HandleReadSideResponse

/**
 * Implements the akka JdbcHandler interface and forwards events to the
 * provided remote read side processor
 *
 * @param eventTag tag for this handler
 * @param processorId read side processor id
 * @param remoteReadProcessor a remote processor to forward events to
 */
private[readside] class ReadSideJdbcHandler(eventTag: String,
                                            processorId: String,
                                            remoteReadProcessor: RemoteReadSideProcessor
) extends JdbcHandler[EventEnvelope[EventWrapper], JdbcSession] {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  /**
   * process an event inside the jdbc session by invoking the remote
   * read processor
   *
   * @param session a JdbcSession implementation
   * @param envelope the wrapped event to process
   */
  def process(session: JdbcSession, envelope: EventEnvelope[EventWrapper]): Unit = {
    // extract required arguments
    val event: ProtoAny = envelope.event.getEvent
    val resultingState: ProtoAny = envelope.event.getResultingState
    val meta: MetaData = envelope.event.getMeta

    // invoke remote processor, get result
    val responseAttempt: Try[HandleReadSideResponse] =
      remoteReadProcessor.processEvent(event, eventTag, resultingState, meta)

    responseAttempt match {
      // handle successful response
      case Success(response) if response.successful =>
        logger.debug(s"success for id=${meta.entityId}, revisionNumber=${meta.revisionNumber}")

      // handle successful gRPC call where server indicated "successful = false"
      case Success(_) =>
        val errMsg: String =
          s"read side returned failure, processor=${processorId}, id=${meta.entityId}, revisionNumber=${meta.revisionNumber}"
        logger.warn(errMsg)
        // FIXME: is this correct?
        throw new RuntimeException(errMsg)

      // handle failed gRPC call
      case Failure(exception) =>
        logger.warn(
          s"read side processing failure, processor=${processorId}, id=${meta.entityId}, revisionNumber=${meta.revisionNumber}, cause=${exception.getMessage()}"
        )
        logger.debug("remote handler failure", exception)
        // FIXME: is this correct?
        throw exception
    }
  }
}
