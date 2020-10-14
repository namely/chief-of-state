import play.grpc.gen.scaladsl.PlayScalaServerCodeGenerator

enablePlugins(DockerComposePlugin)
dockerImageCreationTask := (Docker / publishLocal in `chiefofstate`).value

lazy val root: Project = project
  .in(file("."))
  .aggregate(protogen, `chiefofstate`)
  .settings(publishArtifact := false, skip in publish := true)

lazy val `chiefofstate`: Project = project
  .in(file("code/service"))
  .enablePlugins(LagomScala)
  .enablePlugins(PlayAkkaHttp2Support)
  .enablePlugins(BuildSettings)
  .enablePlugins(DockerSettings)
  .settings(name := "chiefofstate", javaAgents += Dependencies.Compile.KanelaAgent)
  .dependsOn(protogen)

lazy val protogen: Project = project
  .in(file("code/.protogen"))
  .enablePlugins(AkkaGrpcPlugin)
  .enablePlugins(ProtocRuntime)
  .settings(name := "protogen")
  .settings(
    inConfig(Compile)(
      Seq(
        PB.protoSources := Seq(
          // instruct scalapb to build all COS protos
          file("proto/chief-of-state-protos/chief_of_state"),
          file("proto/internal")
        ),
        PB.includePaths := Seq(
          // includes the protobuf source for imports
          file("proto/chief-of-state-protos"),
          file("proto/internal"),
          // includes external protobufs (like google dependencies)
          baseDirectory.value / "target/protobuf_external"
        )
      )
    ),
    // Using Scala
    akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Scala),
    akkaGrpcExtraGenerators in Compile += PlayScalaServerCodeGenerator,
    akkaGrpcGeneratedSources := Seq(AkkaGrpc.Server),
    akkaGrpcCodeGeneratorSettings += "server_power_apis",
    akkaGrpcCodeGeneratorSettings := akkaGrpcCodeGeneratorSettings.value.filterNot(_ == "flat_package")
  )
