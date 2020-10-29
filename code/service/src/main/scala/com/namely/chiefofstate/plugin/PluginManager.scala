package com.namely.chiefofstate.plugin

import com.typesafe.config.Config

import scala.reflect.runtime.universe

/**
 * Active Plugins class to house an instance of plugins
 *
 * @param plugins Sequence of PluginBase
 */
case class PluginManager(plugins: Seq[PluginBase])

/**
 * Companion object for Active Plugins
 */
object PluginManager {

  final val HOCON_PATH: String = "chief-of-state.plugin-settings.enabled-plugins"

  /**
   * Default COS Plugins
   */
  final val DEFAULT_PLUGINS: Seq[String] = Seq(
    "com.namely.chiefofstate.plugin.PersistHeaders"
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
