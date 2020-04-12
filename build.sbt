import com.namely.chiefofstate.{Common, Dependencies}

lazy val root = project
  .in(file("chief-of-state"))
  .enablePlugins(Common)
  .settings(
    name := "chief-of-state",
    PB.protoSources in Compile ++= Seq(file("protos/chief_of_state")),
    PB.includePaths in Compile ++= Seq(file("protos/")),
    excludeFilter in PB.generate := new SimpleFileFilter(
      (f: File) => f.getAbsolutePath.contains("google/protobuf/")
    ),
    PB.targets in Compile := Seq(
      scalapb
        .gen(flatPackage = false, javaConversions = false, grpc = false) -> (sourceManaged in Compile).value
    ),
    libraryDependencies ++= Seq(
      Dependencies.Compile.lagomCommon,
      Dependencies.Runtime.scalapbRuntime
    )
  )
