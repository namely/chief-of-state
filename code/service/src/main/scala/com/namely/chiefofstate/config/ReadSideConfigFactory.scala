package com.namely.chiefofstate.config

object ReadSideConfigFactory {
  val READ_SIDE_HOST_KEY: String = "HOST"
  val READ_SIDE_PORT_KEY: String = "PORT"

  /**
   * Extracts read side configurations from environment variables
   *
   * @return Seq[GrpcReadSideSetting]
   */
  def getReadSideSettings: Seq[ReadSideConfig] = {
    // let us read the env vars
    val envVars: Map[String, String] = sys.env.filter(pair => {
      val (key, _) = pair
      key.startsWith("COS_READ_SIDE_CONFIG__")
    })

    if (envVars.isEmpty) throw new RuntimeException("No readSide configuration is set...")

    if (envVars.keySet.exists(v => v.split("__").length != 3)) {
      throw new RuntimeException("One or more of the read side configurations is invalid")
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
          val readSideConfig: ReadSideConfig =
            settings.foldLeft(ReadSideConfig(processorId))({
              case (config, (key, value)) =>
                if (key == READ_SIDE_HOST_KEY) {
                  config.copy(host = Some(value))
                } else if (key == READ_SIDE_PORT_KEY) {
                  config.copy(port = Some(value).map(_.toInt))
                } else {
                  config.addSetting(key, value)
                }
            })

          // Requires Host and Port to be defined per GrpcReadSideSetting
          require(readSideConfig.host.isDefined, s"ProcessorId $processorId is missing a HOST")
          require(readSideConfig.port.isDefined, s"ProcessorId $processorId is missing a PORT")

          readSideConfig
      })
      .toSeq
  }
}
