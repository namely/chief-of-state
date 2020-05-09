package com.namely.chiefofstate

import sbt._
import com.lightbend.lagom.core.LagomVersion

object Dependencies {
  // Package versions
  object Versions {
    val scala213 = "2.13.1"
    val lagomCommonVersion = "0.0.22"
    val lagomOpenApiVersion = "1.1.0"
    val swaggerAnnotationsVersion = "2.1.1"
    val kanelaAgentVersion = "1.0.5"
    val akkaVersion: String = LagomVersion.akka
    val silencerVersion = "1.6.0"
  }

  object Compile {
    val lagomCommon = "com.namely" %% "lagom-common" % Versions.lagomCommonVersion
    val lagomCommonUtil = "com.namely" %% "lagom-common-util" % Versions.lagomCommonVersion
    val lagomOpenApi = "org.taymyr.lagom" %% "lagom-openapi-scala-api" % Versions.lagomOpenApiVersion
    val swaggerAnnotations = "io.swagger.core.v3" % "swagger-annotations" % Versions.swaggerAnnotationsVersion
    val kanelaAgent = "io.kamon" % "kanela-agent" % Versions.kanelaAgentVersion
  }

  object Runtime {
    val lagomCommonRuntime = "com.namely" %% "lagom-common-runtime" % Versions.lagomCommonVersion % "protobuf"
    val grpcNetty = "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion
    val scalapbGrpcRuntime = "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion
    val scalapbRuntime = "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"
  }

  object Test {
    final val Test = sbt.Test
    val lagomCommonTestkit = "com.namely" %% "lagom-common-testkit" % Versions.lagomCommonVersion % Test
    val akkaGrpcTestkit = "com.lightbend.play" %% "lagom-scaladsl-grpc-testkit" % "0.8.2"
  }

}
