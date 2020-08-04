import sbt.{plugins, AutoPlugin, Plugins}
import sbt.Keys.libraryDependencies

object LagomApi extends AutoPlugin {
  override def requires: Plugins = plugins.JvmPlugin

  override def projectSettings =
    Seq(
      libraryDependencies ++= Seq(
        Dependencies.Compile.Lagompb,
        Dependencies.Runtime.LagompbRuntime,
        Dependencies.Test.AkkaGrpcTestkit
      )
    )
}
