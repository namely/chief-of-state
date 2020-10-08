import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import com.typesafe.sbt.packager.docker.DockerPlugin
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{
  dockerBaseImage,
  dockerGroupLayers,
  dockerRepository,
  dockerUsername
}
import com.typesafe.sbt.packager.Keys.{dockerUpdateLatest, executableScriptName}
import sbt.{plugins, AutoPlugin, Plugins}

object DockerSettings extends AutoPlugin {
  override def requires: Plugins = plugins.JvmPlugin && DockerPlugin && JavaAppPackaging

  override def projectSettings =
    Seq(
      dockerBaseImage := "openjdk:11-jre-slim",
      // since we're manually packaging, just put all files in a single folder "0"
      dockerGroupLayers := { case (_: java.io.File, _: String) => 0 },
      executableScriptName := "entrypoint"
    )
}
