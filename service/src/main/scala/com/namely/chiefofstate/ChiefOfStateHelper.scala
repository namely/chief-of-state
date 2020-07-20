package com.namely.chiefofstate

import com.google.protobuf.any.Any

import scala.util.{Failure, Success, Try}

/**
 * Utility methods
 */
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

  /**
   * Extracts read side configurations from environment variables
   *
   * @return Seq[GrpcReadSideConfig]
   */
  def getReadSideConfigs: Seq[GrpcReadSideConfig] = {

    val envVars: Map[String, String] = sys.env
      .filter(_._1.startsWith("COS_READSIDE_CONFIG__"))

    if(envVars.exists(_._1.split("__").length != 3)) {
      throw new Exception("One or more of the read side configurations is invalid")
    }

    val configurations: Map[String, Iterable[(String, String)]] = envVars
      .groupMap(_._1.split("__").last)({ case(k, v) =>
        val settingName: String = k.split("__").tail.head
        require(settingName != "", s"Setting must be defined in $k")

        settingName -> v
      })

    configurations.map({case (processorId, settings) =>
      var grpcReadSideConfig: GrpcReadSideConfig = GrpcReadSideConfig(processorId)

      settings.foreach({case (key, value) =>
        if(key == "HOST") {
          grpcReadSideConfig = grpcReadSideConfig.copy(host = Some(value))
        } else if(key == "PORT") {
          grpcReadSideConfig = grpcReadSideConfig.copy(port = Some(value).map(_.toInt))
        } else {
          grpcReadSideConfig.addSetting(key, value)
        }
      })

      // Requires Host and Port to be defined per GrpcReadSideConfig
      require(grpcReadSideConfig.host.isDefined, s"ProcessorId $processorId is missing a HOST")
      require(grpcReadSideConfig.port.isDefined, s"ProcessorId $processorId is missing a PORT")

      grpcReadSideConfig
    }).toSeq
  }
}
