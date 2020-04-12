package com.namely.chiefofstate

import sbt._

object Dependencies {
  // Package versions
  object Versions {
    val scala213 = "2.13.1"
    val lagomCommonVersion = "pr-81-413"
    val lagomOpenApiVersion = "1.1.0"
    val swaggerAnnotationsVersion = "2.1.1"
    val kanelaAgentVersion = "1.0.3"
    val akkaVersion = "2.6.4"
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
    val scalapbRuntime = "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"
  }

  object Test {
    final val Test = sbt.Test
    val lagomCommonTestkit = "com.namely" %% "lagom-common-testkit" % Versions.lagomCommonVersion % Test
  }

}
