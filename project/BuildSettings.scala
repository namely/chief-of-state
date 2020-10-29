import com.lightbend.sbt.javaagent.JavaAgent.JavaAgentKeys.javaAgents
import sbt.{AutoPlugin, Plugins, plugins}
import sbt.Keys.{dependencyOverrides, libraryDependencies}

object BuildSettings extends AutoPlugin {
  override def requires: Plugins = plugins.JvmPlugin

  override def projectSettings =
    Seq(
      javaAgents += Dependencies.Compile.KanelaAgent,
      libraryDependencies ++= Seq(
        Dependencies.Compile.GrpcIo,
        Dependencies.Compile.Lagompb,
        Dependencies.Compile.LagompbReadSide,
        Dependencies.Compile.KamonAkkaGrpc,
        Dependencies.Compile.KamonBundle,
        Dependencies.Compile.KamonJaeger,
        Dependencies.Compile.KamonZipkin,
        Dependencies.Compile.KamonPrometheus,
        Dependencies.Compile.Janino,
        Dependencies.Compile.LogstashLogback,
        Dependencies.Runtime.LagompbRuntime,
        Dependencies.Test.AkkaGrpcTestkit
      ),
      dependencyOverrides ++= Dependencies.AkkaOverrideDeps
    )
}
