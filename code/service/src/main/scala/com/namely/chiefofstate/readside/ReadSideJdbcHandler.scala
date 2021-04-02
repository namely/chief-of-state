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

import scala.util.{Failure, Success, Try}
import org.slf4j.{Logger, LoggerFactory}
import com.namely.protobuf.chiefofstate.v1.common.MetaData
import com.namely.protobuf.chiefofstate.v1.readside.HandleReadSideResponse

import java.time.Duration
import scala.annotation.tailrec

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
                                            remoteReadProcessor: RemoteReadSideProcessor,
                                            backOffSecondsMin: Long,
                                            backOffSecondsMax: Long
) extends JdbcHandler[EventEnvelope[EventWrapper], JdbcSession] {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  val backOffMultiplier: Double = Math.random()

  /**
   * @param session a JdbcSession implementation
   * @param envelope the wrapped event to process
   */
  final def process(session: JdbcSession, envelope: EventEnvelope[EventWrapper]): Unit = {
    recursiveProcess(session, envelope)
  }

  /**
   * process an event inside the jdbc session by invoking the remote
   * read processor. In the failure state, backs off and tries again.
   *
   * @param session a JdbcSession implementation
   * @param envelope the wrapped event to process
   * @param numAttempts the number of attempts
   * @param backOffSeconds the seconds to backoff in a failure case
   */
  @tailrec
  final def recursiveProcess(session: JdbcSession,
                             envelope: EventEnvelope[EventWrapper],
                             numAttempts: Int = 1,
                             backOffSeconds: Long = backOffSecondsMin
  ): Unit = {
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
          s"read side returned failure, attempt=${numAttempts}, processor=${processorId}, id=${meta.entityId}, revisionNumber=${meta.revisionNumber}"
        logger.warn(errMsg)
        throw new RuntimeException(errMsg)

      // handle failed gRPC call
      case Failure(exception) =>
        logger.error(
          s"read side processing failure, attempt=${numAttempts}, processor=${processorId}, id=${meta.entityId}, revisionNumber=${meta.revisionNumber}, cause=${exception.getMessage}"
        )
        // for debug purposes, log the stack trace as well
        logger.debug("remote handler failure", exception)

        Thread.sleep(Duration.ofSeconds(backOffSeconds).toMillis)

        val newBackOffSeconds: Long = if (backOffSeconds >= backOffSecondsMax) {
          backOffSecondsMax
        } else {
          backOffSeconds + Math.pow(Math.random() * 2, numAttempts).toLong
        }

        recursiveProcess(session, envelope, numAttempts + 1, newBackOffSeconds)
    }
  }
}
