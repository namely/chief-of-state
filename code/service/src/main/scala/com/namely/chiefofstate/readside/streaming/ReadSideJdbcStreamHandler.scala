/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.readside.streaming

import akka.projection.eventsourced.EventEnvelope
import akka.projection.jdbc.scaladsl.JdbcHandler
import akka.projection.jdbc.JdbcSession
import com.namely.protobuf.chiefofstate.v1.persistence.EventWrapper

/**
 *  Implements the the akka projection JdbcHandler interface and forwards events to the
 *  stream readside handler
 *
 * @param processorId read side processor id
 * @param readSideStreamHandler a remote handler implementation
 */
private[readside] class ReadSideJdbcStreamHandler(processorId: String, readSideStreamHandler: ReadSideStreamHandler)
    extends JdbcHandler[Seq[EventEnvelope[EventWrapper]], JdbcSession] {

  override def process(session: JdbcSession, envelopes: Seq[EventEnvelope[EventWrapper]]): Unit = {
    // construct the list of events to push out
    val events = envelopes.map(envelope => {
      (envelope.event.getEvent, envelope.event.getResultingState, envelope.event.getMeta)
    })

    // send to the remote gRPC stream handler
    readSideStreamHandler.processEvents(events)
  }
}
