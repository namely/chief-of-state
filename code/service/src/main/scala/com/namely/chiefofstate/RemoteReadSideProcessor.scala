package com.namely.chiefofstate

import com.namely.protobuf.chiefofstate.v1.common.MetaData
import com.namely.protobuf.chiefofstate.v1.readside.{HandleReadSideRequest, HandleReadSideResponse}
import com.namely.protobuf.chiefofstate.v1.readside.ReadSideHandlerServiceGrpc.ReadSideHandlerServiceBlockingStub

import scala.util.Try

class RemoteReadSideProcessor(readSideHandlerServiceBlockingStub: ReadSideHandlerServiceBlockingStub) {

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
      readSideHandlerServiceBlockingStub.handleReadSide(
        HandleReadSideRequest()
          .withEvent(event)
          .withState(resultingState)
          .withMeta(meta)
      )
    }
  }
}
