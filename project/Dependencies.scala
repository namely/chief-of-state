import sbt.{Test, _}
import scalapb.compiler.Version.{grpcJavaVersion, scalapbVersion}

object Dependencies {

  // Package versions
  object Versions {
    val ScalaVersion: String = "2.13.3"
    val AkkaVersion: String = "2.6.10"
    val SilencerVersion: String = "1.7.0"
    val LogbackVersion: String = "1.2.3"
    val ScalapbCommonProtoVersion: String = "1.18.0-0"
    val ScalapbValidationVersion: String = "0.1.4"
    val ScalaTestVersion: String = "3.2.2"
    val AkkaManagementVersion: String = "1.0.9"
    val AkkaProjectionVersion: String = "1.0.0"
    val PostgresDriverVersion: String = "42.2.18"
    val SlickVersion: String = "3.3.3"
    val AkkaPersistenceJdbcVersion: String = "4.0.0"
    val ScalaMockVersion: String = "4.4.0"
    val GrpcMockVersion: String = "0.3.4"

    val KanelaAgentVersion: String = "1.0.6"
    val KamonVersion: String = "2.1.8"
    val JaninoVersion: String = "3.1.2"
    val LogstashLogbackVersion: String = "6.3"

    val OpenTracing: String = "0.2.3"
    val OpenTracingJaeger: String = "1.4.0"
  }

  import Dependencies.Versions._

  val kanelaAgent: ModuleID = "io.kamon" % "kanela-agent" % Versions.KanelaAgentVersion

  val jars: Seq[ModuleID] = Seq(
    "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.10" % ScalapbCommonProtoVersion,
    "com.thesamet.scalapb" %% "scalapb-runtime" % scalapbVersion % "protobuf",
    "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapbVersion,
    "com.thesamet.scalapb" %% "scalapb-validate-core" % scalapb.validate.compiler.BuildInfo.version % "protobuf",
    "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.10" % ScalapbCommonProtoVersion % "protobuf",
    "io.grpc" % "grpc-netty" % grpcJavaVersion,
    "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
    "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,
    "ch.qos.logback" % "logback-classic" % LogbackVersion,
    "com.typesafe.akka" %% "akka-cluster-typed" % AkkaVersion,
    "com.typesafe.akka" %% "akka-stream-typed" % AkkaVersion,
    "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
    "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % AkkaManagementVersion,
    "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % AkkaManagementVersion,
    "com.lightbend.akka.management" %% "akka-management-cluster-http" % AkkaManagementVersion,
    "com.lightbend.akka" %% "akka-projection-core" % AkkaProjectionVersion,
    "com.lightbend.akka" %% "akka-projection-slick" % AkkaProjectionVersion,
    "com.lightbend.akka" %% "akka-projection-kafka" % AkkaProjectionVersion,
    "com.lightbend.akka" %% "akka-projection-eventsourced" % Versions.AkkaProjectionVersion,
    "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
    "org.postgresql" % "postgresql" % Versions.PostgresDriverVersion,
    "com.lightbend.akka" %% "akka-persistence-jdbc" % AkkaPersistenceJdbcVersion,
    "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
    "com.typesafe.slick" %% "slick" % SlickVersion,
    "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion,
    "net.logstash.logback" % "logstash-logback-encoder" % Versions.LogstashLogbackVersion,
    "org.codehaus.janino" % "janino" % Versions.JaninoVersion,
    "org.scala-lang" % "scala-reflect" % Versions.ScalaVersion,

    // "io.kamon" %% "kamon-bundle" % Versions.KamonVersion,
    // "io.kamon" %% "kamon-prometheus" % Versions.KamonVersion,
    // "io.kamon" %% "kamon-jaeger" % Versions.KamonVersion,
    // "io.kamon" %% "kamon-zipkin" % Versions.KamonVersion,
    // kanelaAgent,

    // opentracing
    "io.opentracing.contrib" % "opentracing-grpc" % Versions.OpenTracing,
    "io.jaegertracing" % "jaeger-client" % Versions.OpenTracingJaeger
  )

  val testJars: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
    "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,
    "org.scalamock" %% "scalamock" % ScalaMockVersion % Test,
    "org.grpcmock" % "grpcmock-core" % GrpcMockVersion % Test,
    "io.grpc" % "grpc-testing" % grpcJavaVersion % Test
  )

}
