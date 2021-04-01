import sbt.{compilerPlugin, plugins, url, AutoPlugin, CrossVersion, Plugins, Resolver, _}
import sbt.Keys.{resolvers, _}
import Dependencies.Versions
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport.{
  headerLicense,
  headerLicenseStyle,
  HeaderLicense,
  HeaderLicenseStyle
}
import org.scalafmt.sbt.ScalafmtPlugin.autoImport.scalafmtOnCompile
import scoverage.ScoverageKeys.coverageExcludedPackages

object Common extends AutoPlugin {

  override def requires: Plugins = plugins.JvmPlugin

  override def trigger = allRequirements

  override def globalSettings =
    Seq(
      scalaVersion := Versions.ScalaVersion,
      organization := "com.namely",
      organizationName := "Namely Inc.",
      organizationHomepage := Some(url("https://www.namely.com/")),
      startYear := Some(2020),
      licenses += ("MIT", new URL("https://opensource.org/licenses/MIT")),
      headerLicenseStyle := HeaderLicenseStyle.SpdxSyntax,
      headerLicense := Some(
        HeaderLicense.Custom(
          """|Copyright (c) 2020 Namely Inc.
             |
             |""".stripMargin
        )
      ),
      developers += Developer(
        "contributors",
        "Contributors",
        "",
        url("https://github.com/namely/chief-of-state/graphs/contributors")
      )
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
      coverageExcludedPackages := "<empty>;com.namely.protobuf.*;" +
        "com.namely.chiefofstate.StartNodeBehaviour;" +
        "com.namely.chiefofstate.StartNode;",
      fork in Test := true
    )
}
