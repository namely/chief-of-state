import sbt._

object Dependencies {

  // Package versions
  object Versions {
    val Scala213 = "2.13.1"
    val LagomPbVersion = "0.9.0"
    val KanelaAgentVersion = "1.0.6"
    val SilencerVersion = "1.6.0"
    val KamonAkkaGrpcVersion = "0.0.9"
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

}
