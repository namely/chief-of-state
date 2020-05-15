package com.namely.chiefofstate

import com.lightbend.lagom.sbt.LagomImport._
import com.lightbend.sbt.javaagent.JavaAgent.JavaAgentKeys.javaAgents
import com.namely.chiefofstate.Dependencies.Compile
import com.namely.chiefofstate.Dependencies.Runtime
import com.namely.chiefofstate.Dependencies.Test
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.dockerBaseImage
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.dockerRepository
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.dockerUsername
import sbt.Keys.libraryDependencies
import sbt.AutoPlugin
import sbt.Plugins
import sbt.plugins

object LagomImpl extends AutoPlugin {
  override def requires: Plugins = plugins.JvmPlugin

  override def projectSettings = Seq(
    dockerBaseImage := "openjdk:11",
    dockerRepository := Some("registry.namely.land"),
    dockerUsername := Some("namely"),
    javaAgents += Dependencies.Compile.kanelaAgent,
    libraryDependencies ++= Seq(
      lagomScaladslApi,
      lagomScaladslKafkaBroker,
      lagomScaladslTestKit,
      lagomScaladslAkkaDiscovery,
      lagomScaladslPersistenceJdbc,
      lagomScaladslCluster,
      Compile.lagomCommon,
      Compile.lagomCommonUtil,
      Test.lagomCommonTestkit,
      Runtime.lagomCommonRuntime,
      Runtime.scalapbRuntime,
      Test.akkaGrpcTestkit,
      Compile.kamonAkkaGrpc
    )
  )
}
