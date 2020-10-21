package com.namely.chiefofstate.persistence

import akka.persistence.typed.{EventAdapter, EventSeq}
import io.superflat.lagompb.protobuf.v1.core.{EventWrapper => LagompbEventWrapper}
import com.namely.protobuf.chiefofstate.v1.persistence.EventWrapper
import com.namely.chiefofstate.Util

/**
 * Akka persistence event adaptor that converts lagom-pb event wrappers
 * to COS wrappers in preparation of dropping the lagom-pb dependency
 */
object CosEventAdapter extends EventAdapter[LagompbEventWrapper, EventWrapper] {

  /**
   * convert lagom-pb EventWrapper to a cos EventWrapper
   *
   * @param e lagom-pb EventWrapper
   * @return cos EventWrapper instance
   */
  def toJournal(e: LagompbEventWrapper): EventWrapper = {
    EventWrapper(
      event = e.event,
      resultingState = e.resultingState,
      meta = e.meta.map(Util.toCosMetaData)
    )
  }

  /**
   * convert cos EventWrapper to a lagom-pb EventWrapper
   *
   * @param p cos EventWrapper
   * @param manifest the manifest used
   * @return lagom-pb EventWrapper instance
   */
  def fromJournal(p: EventWrapper, manifest: String): EventSeq[LagompbEventWrapper] = {
    EventSeq(
      Seq(
        LagompbEventWrapper(
          event = p.event,
          resultingState = p.resultingState,
          meta = p.meta.map(Util.toLagompbMetaData)
        )
      )
    )
  }

  val MANIFEST: String = "com.namely.chiefofstate.persistence.CosEventAdapter"

  def manifest(event: LagompbEventWrapper): String = MANIFEST
}
