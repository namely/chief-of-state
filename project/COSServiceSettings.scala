import com.lightbend.lagom.sbt.LagomPlugin.autoImport.{
  lagomCassandraEnabled,
  lagomKafkaEnabled,
  lagomServiceGatewayAddress,
  lagomServiceLocatorAddress
}
import sbt.{plugins, AutoPlugin, Plugins, _}
import sbt.Keys._

object COSServiceSettings extends AutoPlugin {
  override def requires: Plugins = plugins.JvmPlugin

  override def trigger = allRequirements

  override def globalSettings = Seq(
    description := "Chief of State"
  )

  override def projectSettings =
    Seq(
      lagomCassandraEnabled in ThisBuild := false,
      lagomKafkaEnabled in ThisBuild := false,
      lagomServiceLocatorAddress in ThisBuild := "0.0.0.0",
      lagomServiceGatewayAddress in ThisBuild := "0.0.0.0"
    )
}
