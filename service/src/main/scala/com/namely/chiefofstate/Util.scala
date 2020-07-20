package com.namely.chiefofstate

import com.google.protobuf.any.Any

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
}
