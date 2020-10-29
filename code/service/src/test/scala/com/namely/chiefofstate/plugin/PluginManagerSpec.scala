package com.namely.chiefofstate.plugin

import com.google.protobuf.any
import com.namely.chiefofstate.test.helpers.{EnvironmentHelper, TestSpec}
import com.namely.protobuf.chiefofstate.v1.service.ProcessCommandRequest
import com.typesafe.config.{Config, ConfigFactory, ConfigValue, ConfigValueFactory}
import io.grpc.Metadata
import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers

private[this] object MockPluginBase1 extends PluginBase {
  override val pluginId: String = "MockPluginBase"

  override def makeAny(processCommandRequest: ProcessCommandRequest, metadata: Metadata): Option[any.Any] = None
}

private[this] object MockPluginBase2 extends PluginBase {
  override val pluginId: String = "MockPluginBase"

  override def makeAny(processCommandRequest: ProcessCommandRequest, metadata: Metadata): Option[any.Any] = None
}

class PluginManagerSpec extends TestSpec {

  val packagePrefix: String = this.getClass.getPackage.getName
  val classPackage1: String = PluginManagerSpecCompanion.makeFullyQuailifiedName(packagePrefix, "MockPluginBase1")
  val classPackage2: String = PluginManagerSpecCompanion.makeFullyQuailifiedName(packagePrefix, "MockPluginBase2")

  override def beforeEach(): Unit = {
    super.beforeEach()
    EnvironmentHelper.clearEnv()
  }

  "ActivePlugins" should {
    "reflectPlugins" should {
      "return all plugin instances" in {
        val plugins: Seq[String] = Seq(classPackage1, classPackage2)

        val actual: Seq[PluginBase] = PluginManager.reflectPlugins(plugins)
        val expected: Seq[String] = PluginManager.DEFAULT_PLUGINS ++ plugins

        PluginManagerSpecCompanion.compare(actual, expected)
      }
      "return the default plugins in order" in {
        val actual: Seq[PluginBase] = PluginManager.reflectPlugins()
        PluginManagerSpecCompanion.compare(actual, PluginManager.DEFAULT_PLUGINS)
      }
      "throw on a bad path" in {
        val plugins: Seq[String] = Seq(classPackage1, classPackage2, "not-a-package")

        intercept[ScalaReflectionException](PluginManager.reflectPlugins(plugins))
      }
    }
    "getPlugins" should {
      "return the default plugins if there are no defined plugins" in {
        val configValue: ConfigValue = ConfigValueFactory.fromAnyRef("")
        val config: Config = ConfigFactory.load().withValue(PluginManager.HOCON_PATH, configValue)

        val actual: PluginManager = PluginManager.getPlugins(config)
        PluginManagerSpecCompanion.compare(actual.plugins, PluginManager.DEFAULT_PLUGINS)
      }
      "throw an error if a class cannot be parsed" in {
        val plugins: Seq[String] = Seq(classPackage1, classPackage2, "not-a-package")
        val configValue: ConfigValue = ConfigValueFactory.fromAnyRef(plugins.mkString(","))
        val config: Config = ConfigFactory.load().withValue(PluginManager.HOCON_PATH, configValue)

        intercept[ScalaReflectionException](PluginManager.getPlugins(config))
      }
      "return the plugins" in {
        val plugins: Seq[String] = Seq(classPackage1, classPackage2)
        val configValue: ConfigValue = ConfigValueFactory.fromAnyRef(plugins.mkString(","))
        val config: Config = ConfigFactory.load().withValue(PluginManager.HOCON_PATH, configValue)

        val actual: PluginManager = PluginManager.getPlugins(config)
        val expected: Seq[String] = PluginManager.DEFAULT_PLUGINS ++ plugins
        PluginManagerSpecCompanion.compare(actual.plugins, expected)
      }
    }
  }
}

object PluginManagerSpecCompanion extends Matchers {

  /**
   * Compares the ActivePlugins payload of that of the expected Plugins
   *
   * @param actual Sequence of ActivePlugin instances
   * @param expected Sequence of Package Names
   * @return Assertion
   */
  def compare(actual: Seq[PluginBase], expected: Seq[String]): Assertion = {
    actual.size should be (expected.size)
    actual.map(x => x.getClass.getName.replace("$","")) should
      contain theSameElementsInOrderAs expected
  }

  /**
   * Makes a fully qualified package name based on a prefix and suffix
   *
   * @param prefix Prefix of package
   * @param suffix Suffix of package
   * @return Fully qualified packange name as a String
   */
  def makeFullyQuailifiedName(prefix: String, suffix: String): String = s"$prefix.$suffix"
}
