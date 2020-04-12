import com.namely.chiefofstate.Dependencies
import com.namely.chiefofstate.LagomAkka
import com.namely.chiefofstate.LagomApi
import com.namely.chiefofstate.LagomImpl
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
  .enablePlugins(LagomAkka)
  .settings(
    name := "api",
  )

lazy val service = project
  .in(file("service"))
  .enablePlugins(LagomScala)
  .enablePlugins(JavaAppPackaging, JavaAgent)
  .enablePlugins(LagomImpl)
  .enablePlugins(LagomAkka)
  .settings(
    name := "service",
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
    akkaGrpcGeneratedSources := Seq(AkkaGrpc.Server),
    akkaGrpcExtraGenerators in Compile += PlayScalaServerCodeGenerator,
    akkaGrpcCodeGeneratorSettings += "server_power_apis",
    akkaGrpcCodeGeneratorSettings := akkaGrpcCodeGeneratorSettings.value.filterNot(
      _ == "flat_package"
    )
  )
