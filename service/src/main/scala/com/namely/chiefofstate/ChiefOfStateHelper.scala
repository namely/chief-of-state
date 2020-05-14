package com.namely.chiefofstate

import com.google.protobuf.any.Any

object ChiefOfStateHelper {

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
