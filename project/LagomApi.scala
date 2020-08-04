package com.namely.chiefofstate

import com.lightbend.lagom.sbt.LagomImport.{lagomScaladslApi, lagomScaladslKafkaBroker, lagomScaladslServer}
import com.namely.chiefofstate.Dependencies.{Compile, Runtime, Test}
import sbt.Keys.libraryDependencies
import sbt.{plugins, AutoPlugin, Plugins, _}

object LagomApi extends AutoPlugin {
  override def requires: Plugins = plugins.JvmPlugin

  override def projectSettings = Seq(
    libraryDependencies ++= Seq(
      lagomScaladslApi,
      lagomScaladslServer % Optional,
      lagomScaladslKafkaBroker,
      Compile.lagompb,
      Runtime.lagompbRuntime,
      Test.akkaGrpcTestkit
    )
  )
}
