package com.namely.chiefofstate.plugin

import com.google.protobuf.any
import com.google.protobuf.wrappers.StringValue
import com.namely.chiefofstate.helper.{BaseSpec, EnvironmentHelper}
import com.namely.protobuf.chiefofstate.v1.service.ProcessCommandRequest
import com.typesafe.config.{Config, ConfigFactory, ConfigValue, ConfigValueFactory}
import io.grpc.{Metadata, Status, StatusException}
import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers

import scala.util.Try

private[this] class MockPlugin1() extends PluginBase {
  override val pluginId: String = "MockPluginBase"

  override def run(processCommandRequest: ProcessCommandRequest, metadata: Metadata): Option[any.Any] = None
}

private[this] object MockPlugin1 extends PluginFactory {
  override def apply(): PluginBase = new MockPlugin1()
}

private[this] class MockPlugin2() extends PluginBase {
  override val pluginId: String = "MockPluginBase"

  override def run(processCommandRequest: ProcessCommandRequest, metadata: Metadata): Option[any.Any] = None
}

private[this] object MockPlugin2 extends PluginFactory {
  override def apply(): PluginBase = new MockPlugin2()
}

class PluginManagerSpec extends BaseSpec {

  val packagePrefix: String = this.getClass.getPackage.getName
  val classPackage1: String = PluginManagerSpecCompanion.makeFullyQuailifiedName(packagePrefix, "MockPlugin1")
  val classPackage2: String = PluginManagerSpecCompanion.makeFullyQuailifiedName(packagePrefix, "MockPlugin2")

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
    "run" should {
      val foo: String = "foo"
      val pluginId: String = "pluginId"
      val processCommandRequest: ProcessCommandRequest = ProcessCommandRequest.defaultInstance
      val metadataKey: Metadata.Key[String] = Metadata.Key.of(foo, Metadata.ASCII_STRING_MARSHALLER)
      val metadata: Metadata = new Metadata()
      metadata.put(metadataKey, foo)

      "return the Option of the String packed as a proto Any" in {

        val anyProto: com.google.protobuf.any.Any = com.google.protobuf.any.Any.pack(StringValue(foo))

        val mockPluginBase: PluginBase = mock[PluginBase]
        (() => mockPluginBase.pluginId).expects().returning(pluginId)
        (mockPluginBase.run _).expects(processCommandRequest, metadata).returning(Some(anyProto))
        val pluginManager: PluginManager = new PluginManager(Seq(mockPluginBase))

        val result: Try[Map[String, com.google.protobuf.any.Any]] = pluginManager.run(processCommandRequest, metadata)
        result.isSuccess should be(true)
        result.get.keySet.size should be(1)
        result.get.keySet.contains(pluginId) should be(true)
        result.get(pluginId).unpack[StringValue] should be(StringValue(foo))
      }

      "return None" in {
        val mockPluginBase: PluginBase = mock[PluginBase]
        (mockPluginBase.run _).expects(processCommandRequest, metadata).returning(None)
        val pluginManager: PluginManager = new PluginManager(Seq(mockPluginBase))

        val result: Try[Map[String, com.google.protobuf.any.Any]] = pluginManager.run(processCommandRequest, metadata)
        result.isSuccess should be(true)
        result.get.keySet.size should be(0)
      }

      "return a failure" in {
        val mockPluginBase: PluginBase = mock[PluginBase]

        (mockPluginBase.run _)
          .expects(processCommandRequest, metadata)
          .throws(new RuntimeException("test"))

        (() => mockPluginBase.pluginId)
          .expects()
          .returning("some-plugin-id")

        val pluginManager: PluginManager = new PluginManager(Seq(mockPluginBase))

        val actual = pluginManager.run(processCommandRequest, metadata)
        val actualError = intercept[StatusException](actual.get)

        actualError.getStatus.getCode shouldBe (Status.Code.INTERNAL)
        actualError.getStatus.getDescription() shouldBe ("test")
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
    actual.size should be(expected.size)
    (actual.map(x => x.getClass.getName.replace("$", "")) should
      contain).theSameElementsInOrderAs(expected)
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
