import sbt.{plugins, AutoPlugin, Plugins, _}
import sbt.Keys.dependencyOverrides
import Dependencies.Versions

object LagomAkka extends AutoPlugin {
  override def requires: Plugins = plugins.JvmPlugin

  override def projectSettings: Seq[Def.Setting[_]] =
    Seq(
      // Akka dependencies used by Lagom
      dependencyOverrides ++= Seq(
        "com.typesafe.akka" %% "akka-actor" % Versions.AkkaVersion,
        "com.typesafe.akka" %% "akka-remote" % Versions.AkkaVersion,
        "com.typesafe.akka" %% "akka-cluster" % Versions.AkkaVersion,
        "com.typesafe.akka" %% "akka-cluster-sharding" % Versions.AkkaVersion,
        "com.typesafe.akka" %% "akka-cluster-sharding-typed" % Versions.AkkaVersion,
        "com.typesafe.akka" %% "akka-cluster-tools" % Versions.AkkaVersion,
        "com.typesafe.akka" %% "akka-cluster-typed" % Versions.AkkaVersion,
        "com.typesafe.akka" %% "akka-coordination" % Versions.AkkaVersion,
        "com.typesafe.akka" %% "akka-discovery" % Versions.AkkaVersion,
        "com.typesafe.akka" %% "akka-distributed-data" % Versions.AkkaVersion,
        "com.typesafe.akka" %% "akka-serialization-jackson" % Versions.AkkaVersion,
        "com.typesafe.akka" %% "akka-persistence" % Versions.AkkaVersion,
        "com.typesafe.akka" %% "akka-persistence-query" % Versions.AkkaVersion,
        "com.typesafe.akka" %% "akka-slf4j" % Versions.AkkaVersion,
        "com.typesafe.akka" %% "akka-stream" % Versions.AkkaVersion,
        "com.typesafe.akka" %% "akka-protobuf-v3" % Versions.AkkaVersion,
        "com.typesafe.akka" %% "akka-actor-typed" % Versions.AkkaVersion,
        "com.typesafe.akka" %% "akka-persistence-typed" % Versions.AkkaVersion,
        "com.typesafe.akka" %% "akka-multi-node-testkit" % Versions.AkkaVersion,
        "com.typesafe.akka" %% "akka-testkit" % Versions.AkkaVersion,
        "com.typesafe.akka" %% "akka-stream-testkit" % Versions.AkkaVersion,
        "com.typesafe.akka" %% "akka-actor-testkit-typed" % Versions.AkkaVersion
      )
    )
}
