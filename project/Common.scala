package com.namely.chiefofstate

import com.namely.chiefofstate.Dependencies.Versions
import sbt.Keys.credentials
import sbt.Keys.isSnapshot
import sbt.Keys.resolvers
import sbt.Keys._
import sbt.AutoPlugin
import sbt.Credentials
import sbt.CrossVersion
import sbt.Developer
import sbt.Plugins
import sbt.Resolver
import sbt.compilerPlugin
import sbt.plugins
import sbt.url
import sbt._
import scoverage.ScoverageKeys.coverageExcludedPackages
import scoverage.ScoverageKeys.coverageFailOnMinimum
import scoverage.ScoverageKeys.coverageMinimum

object Common extends AutoPlugin {
  override def requires: Plugins = plugins.JvmPlugin
  override def trigger = allRequirements

  override def globalSettings = Seq(
    scalaVersion := Versions.scala213,
    organization := "com.namely",
    organizationName := "Namely Inc.",
    organizationHomepage := Some(url("https://www.namely.com/")),
    developers += Developer(
      "contributors",
      "Contributors",
      "",
      url("https://github.com/namely/chief-of-state/graphs/contributors")
    ),
    description := "Chief of State",
    coverageMinimum := 90,
    coverageFailOnMinimum := true
  )

  override def projectSettings = Seq(
    scalacOptions ++= Seq(
      "-Xfatal-warnings",
      "-deprecation",
      "-Xlint",
      "-P:silencer:globalFilters=Unused import"
    ),
    credentials ++= Seq(
      Credentials(
        realm = "Artifactory Realm",
        host = "jfrog.namely.land",
        userName = sys.env.getOrElse("JFROG_USERNAME", ""),
        passwd = sys.env.getOrElse("JFROG_PASSWORD", "")
      )
    ),
    resolvers ++= Seq(
      "Artifactory Realm".at(
        "https://jfrog.namely.land/artifactory/data-sbt-release/"
      ),
      "Artima Maven Repository".at("https://repo.artima.com/releases"),
      Resolver.jcenterRepo,
      "Sonatype OSS Snapshots".at(
        "https://oss.sonatype.org/content/repositories/snapshots"
      )
    ) ++ (
      if (isSnapshot.value) {
        Seq(
          "Artifactory Realm Snapshot"
            .at("https://jfrog.namely.land/artifactory/data-sbt-snapshot/")
        )
      } else {
        Seq()
      }
    ),
    libraryDependencies ++= Seq(
      compilerPlugin(
        ("com.github.ghik" % "silencer-plugin" % Versions.silencerVersion)
          .cross(CrossVersion.full)
      ),
      ("com.github.ghik" % "silencer-lib" % Versions.silencerVersion % Provided)
        .cross(CrossVersion.full)
    ),
    coverageExcludedPackages := "<empty>;com.namely.protobuf.*"
  )
}
