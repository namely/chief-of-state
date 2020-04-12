package com.namely.chiefofstate

import com.lightbend.lagom.sbt.LagomImport.lagomScaladslApi
import com.lightbend.lagom.sbt.LagomImport.lagomScaladslKafkaBroker
import com.lightbend.lagom.sbt.LagomImport.lagomScaladslServer
import com.namely.chiefofstate.Dependencies.Compile
import com.namely.chiefofstate.Dependencies.Runtime
import com.namely.chiefofstate.Dependencies.Test
import sbt.Keys.libraryDependencies
import sbt.AutoPlugin
import sbt.Plugins
import sbt.plugins
import sbt._

object LagomApi extends AutoPlugin {
  override def requires: Plugins = plugins.JvmPlugin

  override def projectSettings = Seq(
    libraryDependencies ++= Seq(
      lagomScaladslApi,
      lagomScaladslServer % Optional,
      lagomScaladslKafkaBroker,
      Compile.lagomCommon,
      Compile.swaggerAnnotations,
      Compile.lagomOpenApi,
      Compile.lagomCommonUtil,
      Test.lagomCommonTestkit,
      Runtime.lagomCommonRuntime
    )
  )
}
