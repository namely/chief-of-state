package com.namely.chiefofstate

import com.google.protobuf.any.Any
import com.namely.protobuf.chiefofstate.v1.common.{MetaData => CosMetaData}
import io.superflat.lagompb.protobuf.v1.core.MetaData

/**
 * Utility methods
 */
object Util {

  /**
   * Extracts the proto message package name
   *
   * @param proto the protocol buffer message
   * @return string
   */
  def getProtoFullyQualifiedName(proto: Any): String = {
    proto.typeUrl.split('/').lastOption.getOrElse("")
  }

  /**
   * Converts the lagom-pb MetaData class to the chief-of-state MetaData
   *
   * @param metaData lagom-pb MetaData
   * @return chief-of-state MetaData instance
   */
  def toCosMetaData(metaData: MetaData): CosMetaData = {
    CosMetaData(
      entityId = metaData.entityId,
      revisionNumber = metaData.revisionNumber,
      revisionDate = metaData.revisionDate,
      data = metaData.data
    )
  }

  /**
   * Converts chief-of-state MetaData to lagom-pb MetaData
   *
   * @param metaData COS meta data
   * @return Lagom-pb MetaData instance
   */
  def toLagompbMetaData(metaData: CosMetaData): MetaData = {
    MetaData(
      entityId = metaData.entityId,
      revisionNumber = metaData.revisionNumber,
      revisionDate = metaData.revisionDate,
      data = metaData.data
    )
  }
}
