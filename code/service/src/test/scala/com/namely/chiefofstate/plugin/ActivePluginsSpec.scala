package com.namely.chiefofstate.plugin

import com.namely.chiefofstate.test.helpers.{EnvironmentHelper, TestSpec}
import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers

private[this] object MockPluginBase1 extends PluginBase {
  override val pluginId: String = "MockPluginBase"

  override def makeMeta(any: Any): Option[com.google.protobuf.any.Any] = None
}

private[this] object MockPluginBase2 extends PluginBase {
  override val pluginId: String = "MockPluginBase"

  override def makeMeta(any: Any): Option[com.google.protobuf.any.Any] = None
}

class ActivePluginsSpec extends TestSpec {

  val packagePrefix: String = this.getClass.getPackage.getName
  val classPackage1: String = ActivePluginsSpecCompanion.makeFullyQuailifiedName(packagePrefix, "MockPluginBase1")
  val classPackage2: String = ActivePluginsSpecCompanion.makeFullyQuailifiedName(packagePrefix, "MockPluginBase2")

  override def beforeEach(): Unit = {
    super.beforeEach()
    EnvironmentHelper.clearEnv()
  }

  "ActivePlugins" should {
    "reflectPlugins" should {
      "return all plugin instances" in {
        val plugins: Seq[String] = Seq(classPackage1, classPackage2)

        val actual: Seq[PluginBase] = ActivePlugins.reflectPlugins(plugins)
        val expected: Seq[String] = ActivePlugins.DEFAULT_PLUGINS ++ plugins

        ActivePluginsSpecCompanion.compare(actual, expected)
      }
      "return the default plugins in order" in {
        val actual: Seq[PluginBase] = ActivePlugins.reflectPlugins()
        ActivePluginsSpecCompanion.compare(actual, ActivePlugins.DEFAULT_PLUGINS)
      }
      "throw on a bad path" in {
        val plugins: Seq[String] = Seq(classPackage1, classPackage2, "not-a-package")

        intercept[ScalaReflectionException](ActivePlugins.reflectPlugins(plugins))
      }
    }
    "getPlugins" should {
      "return the default plugins if the env does not exist" in {
        val actual: ActivePlugins = ActivePlugins.getPlugins
        ActivePluginsSpecCompanion.compare(actual.plugins, ActivePlugins.DEFAULT_PLUGINS)
      }
      "return the default plugins if the env is an empty string" in {
        EnvironmentHelper.setEnv(ActivePlugins.ENV_VAR, "")
        val actual: ActivePlugins = ActivePlugins.getPlugins
        ActivePluginsSpecCompanion.compare(actual.plugins, ActivePlugins.DEFAULT_PLUGINS)
      }
      "throw an error if a class cannot be parsed" in {
        val plugins: Seq[String] = Seq(classPackage1, classPackage2, "not-a-package")
        EnvironmentHelper.setEnv(ActivePlugins.ENV_VAR, plugins.mkString(","))
        intercept[ScalaReflectionException](ActivePlugins.getPlugins)
      }
      "return the plugins" in {
        val plugins: Seq[String] = Seq(classPackage1, classPackage2)
        EnvironmentHelper.setEnv(ActivePlugins.ENV_VAR, plugins.mkString(","))
        val actual: ActivePlugins = ActivePlugins.getPlugins
        val expected: Seq[String] = ActivePlugins.DEFAULT_PLUGINS ++ plugins
        ActivePluginsSpecCompanion.compare(actual.plugins, expected)
      }
    }
  }
}

object ActivePluginsSpecCompanion extends Matchers {

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
