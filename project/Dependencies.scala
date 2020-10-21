import sbt._

object Dependencies {

  // Package versions
  object Versions {
    val Scala213: String = "2.13.1"
    val LagomPbVersion: String = "1.0.2+4-ccd75b98-SNAPSHOT"
    val KanelaAgentVersion: String = "1.0.6"
    val SilencerVersion: String = "1.6.0"
    val KamonAkkaGrpcVersion: String = "0.0.9"
    val AkkaVersion: String = "2.6.9"
    val KamonVersion: String = "2.1.8"
    val JaninoVersion: String = "3.1.2"
    val LogstashLogbackVersion = "6.3"
  }

  object Compile {
    val Lagompb: ModuleID = "io.superflat" %% "lagompb-core" % Versions.LagomPbVersion
    val LagompbReadSide = "io.superflat" %% "lagompb-readside" % Versions.LagomPbVersion
    val KamonBundle: ModuleID = "io.kamon" %% "kamon-bundle" % Versions.KamonVersion
    val KamonPrometheus: ModuleID = "io.kamon" %% "kamon-prometheus" % Versions.KamonVersion
    val KamonJaeger: ModuleID = "io.kamon" %% "kamon-jaeger" % Versions.KamonVersion
    val KamonZipkin: ModuleID = "io.kamon" %% "kamon-zipkin" % Versions.KamonVersion
    val KanelaAgent: ModuleID = "io.kamon" % "kanela-agent" % Versions.KanelaAgentVersion
    val KamonAkkaGrpc: ModuleID =
      ("com.github.nezasa" %% "kamon-akka-grpc" % Versions.KamonAkkaGrpcVersion).intransitive()
    val Janino = "org.codehaus.janino" % "janino" % Versions.JaninoVersion
    val LogstashLogback = "net.logstash.logback" % "logstash-logback-encoder" % Versions.LogstashLogbackVersion
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
