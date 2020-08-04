import sbt._

object Dependencies {

  // Package versions
  object Versions {
    val Scala213 = "2.13.1"
    val LagompVersion = "0.6-alpha.1"
    val AkkaVersion: String = "2.6.8"
    val KanelaAgentVersion = "1.0.5"
    val SilencerVersion = "1.6.0"
    val KamonAkkaGrpcVersion = "0.0.9"
  }

  object Compile {
    val Lagompb: ModuleID = "io.superflat" %% "lagompb-core" % Versions.LagompVersion
    val LagompbReadSide = "io.superflat" %% "lagompb-readside" % Versions.LagompVersion

    val KanelaAgent = "io.kamon" % "kanela-agent" % Versions.KanelaAgentVersion
    val KamonAkkaGrpc = "com.github.nezasa" %% "kamon-akka-grpc" % Versions.KamonAkkaGrpcVersion intransitive ()
  }

  object Runtime {
    val LagompbRuntime: ModuleID = "io.superflat" %% "lagompb-core" % Versions.LagompVersion % "protobuf"
  }

  object Test {
    final val Test = sbt.Test
    val AkkaGrpcTestkit = "com.lightbend.play" %% "lagom-scaladsl-grpc-testkit" % "0.8.2"
  }

}
