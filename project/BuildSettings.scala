import sbt.{plugins, AutoPlugin, Plugins}
import sbt.Keys.libraryDependencies

object BuildSettings extends AutoPlugin {
  override def requires: Plugins = plugins.JvmPlugin

  override def projectSettings =
    Seq(
      libraryDependencies ++= Dependencies.jars ++ Dependencies.testJars
    )
}
