package com.namely.chiefofstate

import com.namely.chiefofstate.Dependencies.Versions
import sbt.Keys.dependencyOverrides
import sbt.AutoPlugin
import sbt.Plugins
import sbt.plugins
import sbt._

object LagomAkka extends AutoPlugin {
  override def requires: Plugins = plugins.JvmPlugin

  override def projectSettings = Seq(
    dependencyOverrides ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % Versions.akkaVersion,
      "com.typesafe.akka" %% "akka-remote" % Versions.akkaVersion,
      "com.typesafe.akka" %% "akka-cluster" % Versions.akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding" % Versions.akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-sharding-typed" % Versions.akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-tools" % Versions.akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-typed" % Versions.akkaVersion,
      "com.typesafe.akka" %% "akka-coordination" % Versions.akkaVersion,
      "com.typesafe.akka" %% "akka-discovery" % Versions.akkaVersion,
      "com.typesafe.akka" %% "akka-distributed-data" % Versions.akkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % Versions.akkaVersion,
      "com.typesafe.akka" %% "akka-persistence" % Versions.akkaVersion,
      "com.typesafe.akka" %% "akka-persistence-query" % Versions.akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % Versions.akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % Versions.akkaVersion,
      "com.typesafe.akka" %% "akka-protobuf-v3" % Versions.akkaVersion,
      "com.typesafe.akka" %% "akka-actor-typed" % Versions.akkaVersion,
      "com.typesafe.akka" %% "akka-persistence-typed" % Versions.akkaVersion,
      "com.typesafe.akka" %% "akka-multi-node-testkit" % Versions.akkaVersion,
      "com.typesafe.akka" %% "akka-testkit" % Versions.akkaVersion,
      "com.typesafe.akka" %% "akka-stream-testkit" % Versions.akkaVersion,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % Versions.akkaVersion
    )
  )
}
