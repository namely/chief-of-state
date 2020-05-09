package com.namely.chiefofstate

import com.lightbend.lagom.sbt.LagomPlugin.autoImport.lagomCassandraEnabled
import com.lightbend.lagom.sbt.LagomPlugin.autoImport.lagomKafkaEnabled
import com.lightbend.lagom.sbt.LagomPlugin.autoImport.lagomServiceGatewayAddress
import com.lightbend.lagom.sbt.LagomPlugin.autoImport.lagomServiceLocatorAddress
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
    description := "Chief of State"
  )

  override def projectSettings = Seq(
    lagomCassandraEnabled in ThisBuild := false,
    lagomKafkaEnabled in ThisBuild := false,
    lagomServiceLocatorAddress in ThisBuild := "0.0.0.0",
    lagomServiceGatewayAddress in ThisBuild := "0.0.0.0",
    javaOptions ++= Seq(
      "-Dpidfile.path=/dev/null"
    ),
    scalacOptions ++= Seq(
      "-Xfatal-warnings",
      "-deprecation",
      "-Xlint",
      "-P:silencer:globalFilters=Unused import;deprecated",
      "-P:silencer:globalFilters=Marked as deprecated in proto file;The Materializer now has all methods the ActorMaterializer used to have;Could not find any member to link;unbalanced or unclosed heading"
    ),
    credentials ++= Seq(
      Credentials(
        realm = "Artifactory Realm",
        host = "jfrog.namely.land",
        userName = sys.env.getOrElse("JFROG_USERNAME", ""),
        passwd = sys.env.getOrElse("JFROG_PASSWORD", "")
      )
    ),
    version := sys.env.getOrElse("VERSION", "development"),
    isSnapshot := !version.value.matches("^\\d+\\.\\d+\\.\\d+$"),
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
