import sbt.{compilerPlugin, plugins, url, AutoPlugin, CrossVersion, Developer, Plugins, Resolver, _}
import sbt.Keys.{resolvers, _}
import scoverage.ScoverageKeys.{coverageExcludedPackages, coverageFailOnMinimum, coverageMinimum}
import Dependencies.Versions
import org.scalafmt.sbt.ScalafmtPlugin.autoImport.scalafmtOnCompile

object Common extends AutoPlugin {
  override def requires: Plugins = plugins.JvmPlugin

  override def trigger = allRequirements

  override def globalSettings =
    Seq(
      scalaVersion := Versions.ScalaVersion,
      organization := "com.namely",
      organizationName := "Namely Inc.",
      organizationHomepage := Some(url("https://www.namely.com/")),
      developers += Developer(
        "contributors",
        "Contributors",
        "",
        url("https://github.com/namely/chief-of-state/graphs/contributors")
      ),
      coverageMinimum := 80,
      coverageFailOnMinimum := true
    )

  override def projectSettings =
    Seq(
      scalacOptions ++= Seq(
        "-Xfatal-warnings",
        "-deprecation",
        "-Xlint",
        "-P:silencer:checkUnused",
        "-P:silencer:pathFilters=.protogen[/].*",
        "-P:silencer:globalFilters=Unused import;deprecated",
        "-P:silencer:globalFilters=Marked as deprecated in proto file;Could not find any member to link;unbalanced or unclosed heading"
      ),
      resolvers ++= Seq(Resolver.jcenterRepo, Resolver.sonatypeRepo("public"), Resolver.sonatypeRepo("snapshots")),
      libraryDependencies ++= Seq(
        compilerPlugin(
          ("com.github.ghik" % "silencer-plugin" % Versions.SilencerVersion)
            .cross(CrossVersion.full)
        ),
        ("com.github.ghik" % "silencer-lib" % Versions.SilencerVersion % Provided)
          .cross(CrossVersion.full)
      ),
      scalafmtOnCompile := true,
      // show full stack traces and test case durations
      testOptions in Test += Tests.Argument("-oDF"),
      logBuffered in Test := false,
      coverageExcludedPackages := "<empty>;com.namely.protobuf.*;"
    )
}
