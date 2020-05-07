import com.namely.chiefofstate.Dependencies
import com.namely.chiefofstate.LagomApi
import com.namely.chiefofstate.LagomImpl
import play.grpc.gen.scaladsl.PlayScalaClientCodeGenerator
import play.grpc.gen.scaladsl.PlayScalaServerCodeGenerator

lazy val root = project
  .in(file("."))
  .aggregate(
    api,
    protogen,
    service
  )

lazy val api = project
  .in(file("api"))
  .enablePlugins(LagomApi)
  .settings(
    name := "api",
    coverageExcludedPackages := "<empty>;com.namely.chiefofstate.ChiefOfStateService;"
  )

lazy val service = project
  .in(file("service"))
  .enablePlugins(LagomScala)
  .enablePlugins(JavaAppPackaging, JavaAgent)
  .enablePlugins(LagomImpl)
  .settings(
    name := "service",
    coverageExcludedPackages := "<empty>;com.namely.chiefofstate.SidecarAggregate;" +
      "com.namely.chiefofstate.SidecarApplicationLoader;" +
      "com.namely.chiefofstate.SidecarServiceImpl;" +
      "com.namely.chiefofstate.SidecarGrpcServiceImpl;" +
      "com.namely.chiefofstate.HandlerClient;"
  )
  .dependsOn(
    protogen,
    api
  )

lazy val protogen = project
  .in(file(".protogen"))
  .enablePlugins(AkkaGrpcPlugin)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.Compile.lagomCommon,
      Dependencies.Runtime.lagomCommonRuntime
    ),
    name := "protogen",
  )
  .settings(
    inConfig(Compile)(
      Seq(
        PB.protoSources ++= Seq(file("protos/chief_of_state")),
        PB.includePaths ++= Seq(file("protos/chief_of_state")),
        excludeFilter in PB.generate := new SimpleFileFilter(
          (f: File) => f.getAbsolutePath.contains("google/protobuf/")
        ),
      )
    ),
    // Using Scala
    akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Scala),
    akkaGrpcExtraGenerators in Compile += PlayScalaServerCodeGenerator,
    akkaGrpcExtraGenerators in Compile += PlayScalaClientCodeGenerator,
    akkaGrpcCodeGeneratorSettings += "server_power_apis",
    akkaGrpcCodeGeneratorSettings := akkaGrpcCodeGeneratorSettings.value.filterNot(
      _ == "flat_package"
    )
  )
