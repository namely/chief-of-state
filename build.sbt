import play.grpc.gen.scaladsl.{PlayScalaClientCodeGenerator, PlayScalaServerCodeGenerator}

enablePlugins(DockerComposePlugin)
dockerImageCreationTask := (Docker / publishLocal in `chiefofstate`).value

lazy val root = project
  .in(file("."))
  .aggregate(protogen, `chiefofstate`)
  .settings(publishArtifact := false, skip in publish := true)

lazy val `chiefofstate` = project
  .in(file("code/service"))
  .enablePlugins(LagomScala)
  .enablePlugins(JavaAppPackaging, JavaAgent)
  .enablePlugins(PlayAkkaHttp2Support)
  .enablePlugins(BuildSettings)
  .settings(name := "chiefofstate", javaAgents += Dependencies.Compile.KanelaAgent)
  .dependsOn(protogen)

lazy val protogen = project
  .in(file("code/.protogen"))
  .enablePlugins(AkkaGrpcPlugin)
  .enablePlugins(ProtocRuntime)
  .settings(name := "protogen")
  .settings(
    inConfig(Compile)(
      Seq(
        PB.protoSources := Seq(
          // instruct scalapb to build all COS protos
          file("protos/chief_of_state")
        ),
        PB.includePaths := Seq(
          // includes the protobuf source for imports
          file("protos"),
          // includes external protobufs (like google dependencies)
          baseDirectory.value / "target/protobuf_external"
        )
      )
    ),
    // Using Scala
    akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Scala),
    akkaGrpcExtraGenerators in Compile += PlayScalaServerCodeGenerator,
    akkaGrpcExtraGenerators in Compile += PlayScalaClientCodeGenerator,
    akkaGrpcCodeGeneratorSettings += "server_power_apis",
    akkaGrpcCodeGeneratorSettings := akkaGrpcCodeGeneratorSettings.value.filterNot(_ == "flat_package")
  )
