import play.grpc.gen.scaladsl.PlayScalaServerCodeGenerator

enablePlugins(DockerComposePlugin)
dockerImageCreationTask := (Docker / publishLocal in `chiefofstate`).value

lazy val root: Project = project
  .in(file("."))
  .enablePlugins(NoPublish)
  .aggregate(protogen, `chiefofstate`, `chiefofstateplugins`)

lazy val `chiefofstate`: Project = project
  .in(file("code/service"))
  .enablePlugins(LagomScala)
  .enablePlugins(PlayAkkaHttp2Support)
  .enablePlugins(BuildSettings)
  .enablePlugins(DockerSettings)
  .enablePlugins(NoPublish)
  .settings(
    name := "chiefofstate",
    javaAgents += Dependencies.Compile.KanelaAgent
  )
  .dependsOn(protogen, `chiefofstateplugins`)

lazy val `chiefofstateplugins` = project
  .in(file("code/plugin"))
  .enablePlugins(Common)
  .enablePlugins(LagomScala)
  .enablePlugins(COSPluginSettings)
  .enablePlugins(Publish)
  .dependsOn(protogen)

lazy val protogen: Project = project
  .in(file("code/.protogen"))
  .enablePlugins(AkkaGrpcPlugin)
  .enablePlugins(ProtocRuntime)
  .enablePlugins(NoPublish)
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
