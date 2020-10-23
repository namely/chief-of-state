import sbt.{Resolver, _}
import sbt.Keys._
import scoverage.ScoverageKeys.{coverageFailOnMinimum, coverageMinimum}
import Dependencies.Versions

object COSPluginSettings extends AutoPlugin {
  override def requires: Plugins = sbt.plugins.JvmPlugin

  override def trigger: PluginTrigger = allRequirements

  override def globalSettings = Seq(
    name := "chiefofstateplugins",
    description := "Chief of State Plugins"
  )

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    libraryDependencies += Dependencies.Test.Mockito
  )
}
