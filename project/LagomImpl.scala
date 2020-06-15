package com.namely.chiefofstate

import com.lightbend.lagom.sbt.LagomImport._
import com.lightbend.sbt.javaagent.JavaAgent.JavaAgentKeys.javaAgents
import com.namely.chiefofstate.Dependencies.{Compile, Runtime, Test}
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{dockerBaseImage, dockerRepository, dockerUsername}
import sbt.Keys.libraryDependencies
import sbt.{plugins, AutoPlugin, Plugins}

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
      Compile.lagompb,
      Compile.lagompbReadSide,
      Compile.scalapbCommon,
      Runtime.lagompbRuntime,
      Runtime.scalapbCommonProtos,
      Runtime.scalapbRuntime,
      Test.akkaGrpcTestkit,
      Compile.kamonAkkaGrpc
    )
  )
}
