import sbt.{ plugins, AutoPlugin, _ }
import sbt.Keys.{ publishArtifact, skip, _ }

/**
 * For projects that are to be published
 */
object Publish extends AutoPlugin {
  override def requires: Plugins = plugins.JvmPlugin

  override def trigger = allRequirements

  override def projectSettings = Seq(publishArtifact := true, Test / publishArtifact := false)
}

/**
 * For projects that are not to be published.
 */
object NoPublish extends AutoPlugin {
  override def requires: Plugins = plugins.JvmPlugin

  override def projectSettings = Seq(publishArtifact := false, publish / skip := true)
}
