object JFrog {
  import sbt._

  def getEnvWithWarning(key: String): String = {
    sys.env.get(key) match {
      case Some(value) => value
      case None =>
        println(s"**** WARNING: ENV VAR '$key' MISSING")
        ""
    }
  }

  lazy val jfrogHost: String = "jfrog.namely.land"
  lazy val jfrogUser: String = getEnvWithWarning("JFROG_USERNAME")
  lazy val jfrogPass: String = getEnvWithWarning("JFROG_PASSWORD")

  lazy val credentials = Seq(
    Credentials(
      realm = "Artifactory Realm",
      host = jfrogHost,
      userName = jfrogUser,
      passwd = jfrogPass
    )
  )

  /** Returns the JFrog resolves and includes the snapshots artifact if not a release build
   *
   * @param isSnapshot False if is a release build
   * @return JFrog MavenRepository
   */
  def getResolvers(isSnapshot: Boolean): Seq[MavenRepository] = {
    val snapshotResolvers: Seq[MavenRepository] = if(isSnapshot) {
      Seq("Artifactory Realm Snapshot".at(s"https://$jfrogHost/artifactory/data-sbt-snapshot/"))
    } else {
      Seq()
    }

    Seq("Artifactory Realm".at(s"https://$jfrogHost/artifactory/data-sbt-release/")) ++ snapshotResolvers
  }

  /** Returns the JFrog MavenRepository
   *
   *
   * @param isSnapshot False if is a release build
   * @return JFrog MavenRepository
   */
  def getPublishTo(isSnapshot: Boolean): Some[MavenRepository] = {
    val repo: String = if (isSnapshot) "data-sbt-snapshot" else "data-sbt-release"
    val props: String = if (isSnapshot) s";build.timestamp=" + new java.util.Date().getTime else ""
    val url = s"https://$jfrogHost/artifactory/$repo$props"
    Some("Artifactory Realm".at(url))
  }
}
