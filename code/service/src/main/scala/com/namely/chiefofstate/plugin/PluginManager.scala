package com.namely.chiefofstate.plugin

import com.google.protobuf.any.Any
import com.namely.protobuf.chiefofstate.v1.service.ProcessCommandRequest
import com.typesafe.config.Config
import io.grpc.{Metadata, Status, StatusException, StatusRuntimeException}
import org.slf4j.{Logger, LoggerFactory}
import scala.reflect.runtime.universe
import scala.util.{Failure, Success, Try}

/**
 * Active Plugins class to house an instance of plugins
 *
 * @param plugins Sequence of PluginBase
 */
case class PluginManager(plugins: Seq[PluginBase]) {
  import PluginManager.logger

  /**
   * Given a PluginManager instance, a ProcessCommandRequest instance and io.grpc.Metadata instance, folds left
   * throughout all the plugins and runs them one at a time. The results are mapped into a Map of pluginId -> protoAny.
   * On the failure case, returns a GrpcServiceException.
   *
   * @param processCommandRequest ProcessCommandRequest instance
   * @param metadata io.grpc.Metadata instance
   * @return Try of a Map[String, Any]
   */
  def run(processCommandRequest: ProcessCommandRequest, metadata: Metadata): Try[Map[String, Any]] = {
    plugins.foldLeft(Try(Map[String, Any]()))((metaMap, plugin) => {
      val pluginRun: Try[Map[String, Any]] = Try {
        plugin.run(processCommandRequest, metadata) match {
          case Some(value) => Map(plugin.pluginId -> value)
          case None        => Map.empty[String, com.google.protobuf.any.Any]
        }
      }
      pluginRun match {
        case Success(m) =>
          Try(metaMap.get ++ m)

        case Failure(e: StatusException) =>
          logger.error(s"plugin '${plugin.pluginId}' failed with ${e.getStatus.toString}")
          Failure(e)

        case Failure(e: StatusRuntimeException) =>
          logger.error(s"plugin '${plugin.pluginId}' failed with ${e.getStatus.toString}")
          Failure(e)

        case Failure(e: Throwable) =>
          val errMsg = s"plugin ${plugin.pluginId} failed due to ${e.getClass.getName}: ${e.getMessage}"
          logger.error(errMsg)
          val status = Status.INTERNAL.withDescription(e.getMessage)
          val err = new StatusException(status)
          Failure(err)
      }
    })
  }
}

/**
 * Companion object for Active Plugins
 */
object PluginManager {

  final val HOCON_PATH: String = "chief-of-state.plugin-settings.enable-plugins"

  lazy val logger: Logger = LoggerFactory.getLogger(getClass)

  /**
   * Default COS Plugins
   */
  final val DEFAULT_PLUGINS: Seq[String] = Seq(
    "com.namely.chiefofstate.plugin.PersistedHeaders"
  )

  /**
   * Given a sequence of plugin packages strings, reflects the packages and packs the results
   * into a sequence of PluginBase.
   *
   * @param plugins Sequence of plugin package strings
   * @return Sequence of PluginBase
   */
  def reflectPlugins(plugins: Seq[String] = Seq()): Seq[PluginBase] = {
    (DEFAULT_PLUGINS ++ plugins).map(className => {

      val runtimeMirror: universe.Mirror = universe.runtimeMirror(getClass.getClassLoader)
      val module: universe.ModuleSymbol = runtimeMirror.staticModule(className)
      val obj: universe.ModuleMirror = runtimeMirror.reflectModule(module)

      obj.instance.asInstanceOf[PluginFactory].apply()
    })
  }

  /**
   * Inspects the environment variable and splits the result by commas. If the environment variable
   * does not exist, returns a empty Sequence. Otherwise, calls the reflectPlugins method to return
   * a Sequence of PluginBase
   *
   * @param config HOCON Configurations
   * @return Sequence of PluginBase
   */
  def getPlugins(config: Config): PluginManager = {
    val plugins: Seq[String] = config
      .getString(HOCON_PATH)
      .split(",")
      .toSeq
      .map(_.trim)
      .filter(_.nonEmpty)

    val reflectedPlugins: Seq[PluginBase] = reflectPlugins(plugins)

    new PluginManager(reflectedPlugins)
  }
}
