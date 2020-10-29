import sbt.{compilerPlugin, plugins, url, AutoPlugin, Credentials, CrossVersion, Developer, Plugins, Resolver, _}
import sbt.Keys.{credentials, isSnapshot, resolvers, _}
import scoverage.ScoverageKeys.{coverageExcludedPackages, coverageFailOnMinimum, coverageMinimum}
import Dependencies.Versions

object Common extends AutoPlugin {
  override def requires: Plugins = plugins.JvmPlugin

  override def trigger = allRequirements

  override def globalSettings = Seq(
    scalaVersion := Versions.Scala213,
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

  override def projectSettings = Seq(
    javaOptions ++= Seq("-Dpidfile.path=/dev/null"),
    scalacOptions ++= Seq(
      "-Xfatal-warnings",
      "-deprecation",
      "-Xlint",
      "-P:silencer:checkUnused",
      "-P:silencer:pathFilters=.protogen[/].*",
      "-P:silencer:globalFilters=Unused import;deprecated",
      "-P:silencer:globalFilters=Marked as deprecated in proto file;The Materializer now has all methods the ActorMaterializer used to have;Could not find any member to link;unbalanced or unclosed heading"
    ),
    credentials ++= JFrog.credentials,
    version := sys.env.getOrElse("VERSION", "development"),
    isSnapshot := !version.value.matches("^\\d+\\.\\d+\\.\\d+$"),
    resolvers ++= Seq(
      "Artima Maven Repository".at("https://repo.artima.com/releases"),
      Resolver.jcenterRepo,
      Resolver.sonatypeRepo("public"),
      Resolver.sonatypeRepo("snapshots"),
      "Sonatype OSS Snapshots".at("https://oss.sonatype.org/content/repositories/snapshots")
    ),
    resolvers ++= JFrog.getResolvers(isSnapshot.value),
    libraryDependencies ++= Seq(
      compilerPlugin(
        ("com.github.ghik" % "silencer-plugin" % Versions.SilencerVersion)
          .cross(CrossVersion.full)
      ),
      ("com.github.ghik" % "silencer-lib" % Versions.SilencerVersion % Provided)
        .cross(CrossVersion.full)
    ),
    coverageExcludedPackages := "<empty>;com.namely.protobuf.*;" +
      "com.namely.protobuf.*;" +
      "com.namely.chiefofstate.ChiefOfStateService;" +
      "com.namely.chiefofstate.RestServiceImpl;" +
      "com.namely.chiefofstate.api.*;" +
      "com.namely.chiefofstate.GrpcServiceImpl;" +
      "com.namely.chiefofstate.ApplicationLoader;" +
      "com.namely.chiefofstate.Application;" +
      "com.namely.chiefofstate.Aggregate;" +
      "com.namely.chiefofstate.readside.*;" +
      "com.namely.chiefofstate.grpc.client.*;"
  )
}
