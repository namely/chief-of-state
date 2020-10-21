package com.namely.chiefofstate.persistence

import io.superflat.lagompb.protobuf.v1.core.{StateWrapper => LagompbStateWrapper}
import com.namely.protobuf.chiefofstate.v1.persistence.StateWrapper
import akka.persistence.typed.SnapshotAdapter
import com.google.protobuf.any.Any
import com.namely.chiefofstate.Util

/**
 * akka persistence SnapshotAdapter for converting to/from lagom-pb
 * state wrappers in anticipation of dropping that dependency
 */
object CosSnapshotAdapter extends SnapshotAdapter[LagompbStateWrapper] {

  /**
   * convert lagom-pb state wrapper to a COS wrapper
   *
   * @param state lagom-pb state wrapper
   * @return COS state wrapper as a scala.Any
   */
  def toJournal(state: LagompbStateWrapper): scala.Any = {
    StateWrapper(
      state = state.state,
      meta = state.meta.map(Util.toCosMetaData)
    )
  }

  /**
   * convert COS state wrapper to a lagom-pb state wrapper
   *
   * @param from COS state wrapper as a scala.Any
   * @return lagom-pb StateWrapper
   */
  def fromJournal(from: scala.Any): LagompbStateWrapper = {
    from match {
      case state: StateWrapper =>
        LagompbStateWrapper(
          state = state.state,
          meta = state.meta.map(Util.toLagompbMetaData)
        )

      case x =>
        throw new Exception(s"snapshot adapter cannot unpack state of type ${from.getClass.getName}")
    }
  }

}
