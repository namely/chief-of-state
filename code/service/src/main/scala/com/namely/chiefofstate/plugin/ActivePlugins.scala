package com.namely.chiefofstate.plugin

import scala.reflect.runtime.universe

/** Active Plugins class to house an instance of plugins
 *
 * @param plugins Sequence of PluginBase
 */
case class ActivePlugins(plugins: Seq[PluginBase])

/**
 * Companion object for Active Plugins
 */
object ActivePlugins {

  /** Given a sequence of plugin packages strings, reflects the packages and packs the results
   * into a sequence of PluginBase.
   *
   * @param plugins Sequence of plugin package strings
   * @return Sequence of PluginBase
   */
  def reflectPlugins(plugins: Seq[String]): Seq[PluginBase] = {
    plugins.map(className => {

      val runtimeMirror: universe.Mirror = universe.runtimeMirror(getClass.getClassLoader)
      val module: universe.ModuleSymbol = runtimeMirror.staticModule(className)
      val obj: universe.ModuleMirror = runtimeMirror.reflectModule(module)

      obj.instance.asInstanceOf[PluginBase]
    })
  }

  /** Inspects the environment variable and splits the result by commas. If the environment variable
   * does not exist, returns a empty Sequence. Otherwise, calls the reflectPlugins method to return
   * a Sequence of PluginBase
   *
   * @return Sequence of PluginBase
   */
  def getPlugins: ActivePlugins = {
    val plugins: Seq[PluginBase] = sys.env.get("COS_PLUGIN_PACKAGES") match {
      case Some(s) =>reflectPlugins(s.split(",").map(_.trim))
      case None => Seq()
    }

    new ActivePlugins(plugins)
  }
}
