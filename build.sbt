import play.grpc.gen.scaladsl.{PlayScalaClientCodeGenerator, PlayScalaServerCodeGenerator}

enablePlugins(DockerComposePlugin)
dockerImageCreationTask := (Docker / publishLocal in `chiefofstate`).value

debianPackageDependencies := Seq("java8-runtime-headless")
enablePlugins(DebianPlugin)

lazy val root = project
  .in(file("."))
  .aggregate(api, protogen, `chiefofstate`)
  .settings(publishArtifact := false, skip in publish := true)

lazy val api = project
  .in(file("api"))
  .enablePlugins(LagomApi)
  .enablePlugins(LagomAkka)
  .settings(name := "api")

lazy val `chiefofstate` = project
  .in(file("service"))
  .enablePlugins(LagomScala)
  .enablePlugins(JavaAppPackaging, JavaAgent)
  .enablePlugins(PlayAkkaHttp2Support)
  .enablePlugins(LagomImpl)
  .enablePlugins(LagomAkka)
  .settings(
    name := "chiefofstate",
    javaAgents += Dependencies.Compile.KanelaAgent,
    maintainer := "namely",
    packageSummary in Linux := "Chief of State",
    packageDescription := "Chief of State"
  )
  .dependsOn(protogen, api)

lazy val protogen = project
  .in(file(".protogen"))
  .enablePlugins(AkkaGrpcPlugin)
  .enablePlugins(ProtocRuntime)
  .settings(name := "protogen")
  .settings(
    inConfig(Compile)(
      Seq(
        PB.protoSources ++= Seq(file("protos")),
        PB.includePaths ++= Seq(file("protos")),
        excludeFilter in PB.generate := new SimpleFileFilter(
          (f: File) => f.getAbsolutePath.contains("google/protobuf/")
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
