package com.namely.chiefofstate.readside

import akka.Done
import com.namely.protobuf.chiefofstate.v1.common.MetaData
import slick.dbio.DBIO

trait EventsProcessor {

  /**
   * Processes events read from the Journal
   *
   * @param event the actual event
   * @param eventTag the event tag
   * @param resultingState the resulting state of the applied event
   * @param meta the additional meta data
   * @return
   */
  def process(
    event: com.google.protobuf.any.Any,
    eventTag: String,
    resultingState: com.google.protobuf.any.Any,
    meta: MetaData
  ): DBIO[Done]
}
