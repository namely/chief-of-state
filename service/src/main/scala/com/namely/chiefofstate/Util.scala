package com.namely.chiefofstate

import com.google.protobuf.any.Any
import io.superflat.lagompb.protobuf.core.MetaData
import com.namely.protobuf.chief_of_state.common.{MetaData => CosMetaData}

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
   * Extracts read side configurations from environment variables
   *
   * @return Seq[GrpcReadSideConfig]
   */
  def getReadSideConfigs: Seq[ReadSideConfig] = {

    val envVars: Map[String, String] = sys.env
      .filter(_._1.startsWith("COS_READSIDE_CONFIG__"))

    if (envVars.exists(_._1.split("__").length != 3)) {
      throw new Exception("One or more of the read side configurations is invalid")
    }

    val groupedEnvVars: Map[String, Iterable[(String, String)]] = envVars
      .groupMap(_._1.split("__").last)({
        case (k, v) =>
          val settingName: String = k.split("__").tail.head
          require(settingName != "", s"Setting must be defined in $k")

          settingName -> v
      })

    groupedEnvVars
      .map({
        case (processorId, settings) =>
          val grpcConfig: ReadSideConfig = settings.foldLeft(ReadSideConfig(processorId))({
            case (config, (key, value)) =>
              if (key == Constants.READ_SIDE_HOST_KEY) {
                config.copy(host = Some(value))
              } else if (key == Constants.READ_SIDE_PORT_KEY) {
                config.copy(port = Some(value).map(_.toInt))
              } else {
                config.addSetting(key, value)
              }
          })

          // Requires Host and Port to be defined per GrpcReadSideConfig
          require(grpcConfig.host.isDefined, s"ProcessorId $processorId is missing a HOST")
          require(grpcConfig.port.isDefined, s"ProcessorId $processorId is missing a PORT")

          grpcConfig
      })
      .toSeq
  }

  /** Converts the lagom-pb MetaData class to the chief-of-state MetaData
    *
    * @param metaData
    * @return
    */
  def toCosMetaData(metaData: MetaData): CosMetaData = {
    CosMetaData(
      revisionNumber = metaData.revisionNumber,
      revisionDate = metaData.revisionDate,
      data = metaData.data
    )
  }
}
