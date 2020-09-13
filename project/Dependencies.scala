import sbt._

object Dependencies {

  // Package versions
  object Versions {
    val Scala213 = "2.13.1"
    val LagomPbVersion = "0.9.0+3-5ec5cf5e-SNAPSHOT"
    val KanelaAgentVersion = "1.0.6"
    val SilencerVersion = "1.6.0"
    val KamonAkkaGrpcVersion = "0.0.9"
    val AkkaVersion = "2.6.9"
  }

  object Compile {
    val Lagompb: ModuleID = "io.superflat" %% "lagompb-core" % Versions.LagomPbVersion
    val LagompbReadSide = "io.superflat" %% "lagompb-readside" % Versions.LagomPbVersion

    val KanelaAgent = "io.kamon" % "kanela-agent" % Versions.KanelaAgentVersion
    val KamonAkkaGrpc = ("com.github.nezasa" %% "kamon-akka-grpc" % Versions.KamonAkkaGrpcVersion).intransitive()
  }

  object Runtime {
    val LagompbRuntime: ModuleID = "io.superflat" %% "lagompb-core" % Versions.LagomPbVersion % "protobuf"
  }

  object Test {
    final val Test = sbt.Test
    val AkkaGrpcTestkit = "com.lightbend.play" %% "lagom-scaladsl-grpc-testkit" % "0.8.2" % "test"
  }

  val AkkaOverrideDeps = Seq(
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
}
