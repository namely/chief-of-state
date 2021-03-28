/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.readside

import akka.projection.eventsourced.EventEnvelope
import akka.projection.slick.SlickHandler
import akka.Done
import com.google.protobuf.any
import com.namely.protobuf.chiefofstate.v1.common.MetaData
import com.namely.protobuf.chiefofstate.v1.persistence.EventWrapper
import com.namely.protobuf.chiefofstate.v1.readside.HandleReadSideResponse
import slick.dbio.{DBIO, DBIOAction}

import scala.util.{Failure, Success, Try}

/**
 * consumes events from the journal by tag and make them available for processing
 *
 * @param eventTag the event tage
 * @param processorId the processor unique id
 * @param remoteReadProcessor the actual events processor
 */
class ReadSideEventsConsumer(eventTag: String, processorId: String, remoteReadProcessor: RemoteReadSideProcessor)
    extends SlickHandler[EventEnvelope[EventWrapper]] {

  /**
   * processes the consumed event envelope and commit the offset of the event consumed
   * when successfully handled.
   *
   * @param envelope the event envelope
   * @return
   */
  override def process(envelope: EventEnvelope[EventWrapper]): DBIO[Done] = {
    val event: any.Any = envelope.event.getEvent
    val resultingState: any.Any = envelope.event.getResultingState
    val meta: MetaData = envelope.event.getMeta
    val responseAttempt: Try[HandleReadSideResponse] =
      remoteReadProcessor.processEvent(event, eventTag, resultingState, meta)

    responseAttempt match {
      case Failure(exception) => DBIOAction.failed(exception)
      case Success(value) =>
        if (value.successful) DBIOAction.successful(Done)
        else
          DBIOAction.failed(
            new RuntimeException(
              s"[ChiefOfState]: $processorId - unable to handle readSide"
            )
          )
    }
  }
}
