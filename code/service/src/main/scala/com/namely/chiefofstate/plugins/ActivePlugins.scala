package com.namely.chiefofstate.plugins

import scala.reflect.runtime.universe

object ActivePlugins {

  //TODO: Source from env
  val plugins: Seq[PluginBase] = Seq("com.namely.chiefofstate.plugins.PersistHeaders")
    .map(className => {

      val runtimeMirror: universe.Mirror = universe.runtimeMirror(getClass.getClassLoader)
      val module: universe.ModuleSymbol = runtimeMirror.staticModule(className)
      val obj: universe.ModuleMirror = runtimeMirror.reflectModule(module)

      obj.instance.asInstanceOf[PluginBase]
    })
}
