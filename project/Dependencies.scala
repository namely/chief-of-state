import sbt.{Test, _}
import scalapb.compiler.Version.{grpcJavaVersion, scalapbVersion}

object Dependencies {

  // Package versions
  object Versions {
    val ScalaVersion: String = "2.13.3"
    val AkkaVersion: String = "2.6.11"
    val SilencerVersion: String = "1.7.1"
    val LogbackVersion: String = "1.2.3"
    val ScalapbCommonProtoVersion: String = "1.18.1-0"
    val ScalapbValidationVersion: String = "0.1.4"
    val ScalaTestVersion: String = "3.2.3"
    val AkkaManagementVersion: String = "1.0.9"
    val AkkaProjectionVersion: String = "1.0.0"
    val PostgresDriverVersion: String = "42.2.18"
    val SlickVersion: String = "3.3.3"
    val AkkaPersistenceJdbcVersion: String = "4.0.0"
    val ScalaMockVersion: String = "5.1.0"

    val JaninoVersion: String = "3.1.2"
    val LogstashLogbackVersion: String = "6.3"

    val OpenTracing: String = "0.33.0"
    val OpenTracingGrpc: String = "0.2.3"
    val OpenTracingConcurrent: String = "0.4.0"
    val OpenTracingMetrics: String = "0.3.0"
    val OpenTracingApiExtensions: String = "0.6.0"
    val OpenTracingJaeger: String = "1.5.0"

    val Micrometer: String = "1.6.2"

    val EmbeddedPostgresVersion = "1.2.9"
  }

  import Dependencies.Versions._

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
    // opentracing
    "io.opentracing" % "opentracing-api" % Versions.OpenTracing,
    "io.opentracing" % "opentracing-noop" % Versions.OpenTracing,
    "io.opentracing.contrib" % "opentracing-grpc" % Versions.OpenTracingGrpc,
    "io.jaegertracing" % "jaeger-client" % Versions.OpenTracingJaeger,
    "io.opentracing.contrib" % "opentracing-concurrent" % Versions.OpenTracingConcurrent,
    // metrics
    "io.opentracing.contrib" % "opentracing-metrics" % Versions.OpenTracingMetrics,
    "io.opentracing.contrib" % "opentracing-metrics-parent" % Versions.OpenTracingMetrics,
    "io.opentracing.contrib" % "opentracing-metrics-micrometer" % Versions.OpenTracingMetrics,
    "io.opentracing.contrib" % "opentracing-api-extensions" % Versions.OpenTracingApiExtensions,
    "io.opentracing.contrib" % "opentracing-api-extensions-tracer" % Versions.OpenTracingApiExtensions,
    "io.jaegertracing" % "jaeger-micrometer" % Versions.OpenTracingJaeger,
    "io.micrometer" % "micrometer-core" % Versions.Micrometer,
    "io.micrometer" % "micrometer-registry-prometheus" % Versions.Micrometer
  )

  val testJars: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
    "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,
    "org.scalamock" %% "scalamock" % ScalaMockVersion % Test,
    "io.grpc" % "grpc-testing" % grpcJavaVersion % Test,
    "io.zonky.test" % "embedded-postgres" % EmbeddedPostgresVersion % Test,
    // opentracing test
    "io.opentracing" % "opentracing-mock" % Versions.OpenTracing % Test
  )
}
