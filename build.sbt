parallelExecution in test := false
enablePlugins(DockerComposePlugin)
dockerImageCreationTask := (Docker / publishLocal in chiefofstate).value

lazy val root: Project = project
  .in(file("."))
  .enablePlugins(NoPublish)
  .settings(headerLicense := None)
  .aggregate(protogen, chiefofstate, chiefofstateplugins, protogenTest)

lazy val chiefofstate: Project = project
  .in(file("code/service"))
  .enablePlugins(Common)
  .enablePlugins(BuildSettings)
  .enablePlugins(DockerSettings)
  .enablePlugins(NoPublish)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(name := "chiefofstate", headerLicenseStyle := HeaderLicenseStyle.SpdxSyntax)
  .dependsOn(protogen, chiefofstateplugins, protogenTest % "test->compile")

lazy val chiefofstateplugins = project
  .in(file("code/plugin"))
  .enablePlugins(Common)
  .enablePlugins(BuildSettings)
  .enablePlugins(DockerSettings)
  .enablePlugins(NoPublish)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name := "chiefofstate-plugins",
    description := "Chief of State Plugins",
    headerLicenseStyle := HeaderLicenseStyle.SpdxSyntax
  )
  .dependsOn(protogen)

lazy val protogen: Project = project
  .in(file("code/.protogen"))
  .enablePlugins(Common)
  .enablePlugins(BuildSettings)
  .enablePlugins(NoPublish)
  .settings(name := "protogen")
  .settings(headerLicense := None)
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
        ),
        PB.targets := Seq(
          scalapb.gen(
            flatPackage = false,
            javaConversions = false,
            grpc = true
          ) -> (sourceManaged in Compile).value / "scalapb"
        )
      )
    )
  )

lazy val protogenTest: Project = project
  .in(file("code/.protogen_test"))
  .enablePlugins(Common)
  .enablePlugins(BuildSettings)
  .enablePlugins(NoPublish)
  .settings(name := "protogen_test")
  .settings(headerLicense := None)
  .settings(
    inConfig(Compile)(
      Seq(
        PB.protoSources := Seq(
          file("proto/test")
        ),
        PB.includePaths := Seq(
          file("proto/test"),
          // includes external protobufs (like google dependencies)
          baseDirectory.value / "target/protobuf_external"
        ),
        PB.targets := Seq(
          scalapb.gen(
            flatPackage = false,
            javaConversions = false,
            grpc = true
          ) -> (sourceManaged in Compile).value / "scalapb"
        )
      )
    )
  )
