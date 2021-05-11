import sbt.{ Test, _ }
import scalapb.compiler.Version.{ grpcJavaVersion, scalapbVersion }

object Dependencies {

  // Package versions
  object Versions {
    val ScalaVersion: String = "2.13.5"
    val AkkaVersion: String = "2.6.14"
    val SilencerVersion: String = "1.7.3"
    val LogbackVersion: String = "1.2.3"
    val ScalapbCommonProtoVersion: String = "1.18.1-SNAPSHOT"
    val ScalapbValidationVersion: String = "0.1.4"
    val ScalaTestVersion: String = "3.2.8"
    val AkkaManagementVersion: String = "1.1.0"
    val AkkaProjectionVersion: String = "1.2.0"
    val PostgresDriverVersion: String = "42.2.20"
    val SlickVersion: String = "3.3.3"
    val AkkaPersistenceJdbcVersion: String = "5.0.0"
    val ScalaMockVersion: String = "5.1.0"

    val JaninoVersion: String = "3.1.4"
    val LogstashLogbackVersion: String = "6.3"

    val OpenTelemetryVersion: String = "1.2.0"
    val OpenTelemetryGRPCVersion: String = "1.0.1-alpha"
    val OpenTelemetryMetricsVersion: String = "1.0.1-alpha"
    val PrometheusServerVersion: String = "0.10.0"

    val TestContainers: String = "0.39.3"

    val OtelToolsVersion: String = "0.0.0+1-114ae6f7-SNAPSHOT"
  }

  import Dependencies.Versions._

  val excludeGRPC = ExclusionRule(organization = "io.grpc")
  val jars: Seq[ModuleID] = Seq(
    "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % ScalapbCommonProtoVersion,
    "com.thesamet.scalapb" %% "scalapb-runtime" % scalapbVersion % "protobuf",
    "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapbVersion,
    "com.thesamet.scalapb" %% "scalapb-validate-core" % scalapb.validate.compiler.BuildInfo.version % "protobuf",
    "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % ScalapbCommonProtoVersion % "protobuf",
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
    "com.lightbend.akka" %% "akka-projection-kafka" % AkkaProjectionVersion,
    "com.lightbend.akka" %% "akka-projection-eventsourced" % Versions.AkkaProjectionVersion,
    "com.lightbend.akka" %% "akka-projection-jdbc" % Versions.AkkaProjectionVersion,
    "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
    "org.postgresql" % "postgresql" % Versions.PostgresDriverVersion,
    "com.lightbend.akka" %% "akka-persistence-jdbc" % AkkaPersistenceJdbcVersion,
    "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
    "com.typesafe.slick" %% "slick" % SlickVersion,
    "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion,
    "net.logstash.logback" % "logstash-logback-encoder" % Versions.LogstashLogbackVersion,
    "org.codehaus.janino" % "janino" % Versions.JaninoVersion,
    "org.scala-lang" % "scala-reflect" % Versions.ScalaVersion,
    // Opentelemetry
    "io.opentelemetry" % "opentelemetry-api" % OpenTelemetryVersion,
    "io.opentelemetry" % "opentelemetry-sdk" % OpenTelemetryVersion,
    "io.opentelemetry.instrumentation" % "opentelemetry-grpc-1.5" % OpenTelemetryGRPCVersion,
    "io.opentelemetry" % "opentelemetry-extension-trace-propagators" % OpenTelemetryVersion,
    ("io.opentelemetry" % "opentelemetry-exporter-otlp-trace" % OpenTelemetryVersion).excludeAll(excludeGRPC),
    ("io.opentelemetry" % "opentelemetry-exporter-otlp-metrics" % OpenTelemetryMetricsVersion).excludeAll(excludeGRPC),
    ("io.opentelemetry" % "opentelemetry-exporter-jaeger-thrift" % OpenTelemetryVersion).excludeAll(excludeGRPC),
    "io.opentelemetry" % "opentelemetry-exporter-prometheus" % OpenTelemetryMetricsVersion,
    "io.opentelemetry" % "opentelemetry-sdk-testing" % OpenTelemetryVersion % Test,
    "io.prometheus" % "simpleclient_httpserver" % PrometheusServerVersion,
    // Otel tools
    "io.superflat" % "otel-tools_2.13" % OtelToolsVersion
  )

  val testJars: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion % Test,
    "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,
    "org.scalamock" %% "scalamock" % ScalaMockVersion % Test,
    "io.grpc" % "grpc-testing" % grpcJavaVersion % Test,
    // test containers
    "com.dimafeng" %% "testcontainers-scala-scalatest" % Versions.TestContainers % Test,
    "com.dimafeng" %% "testcontainers-scala-postgresql" % Versions.TestContainers % Test
  )
}
