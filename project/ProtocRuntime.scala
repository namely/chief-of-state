package com.namely.chiefofstate

import com.namely.chiefofstate.Dependencies.{Compile, Runtime}
import sbt.{plugins, AutoPlugin, Plugins}
import sbt.Keys.libraryDependencies

object ProtocRuntime extends AutoPlugin {
  override def requires: Plugins = plugins.JvmPlugin

  override def projectSettings =
    Seq(
      libraryDependencies ++= Seq(
        Compile.lagompb,
        Runtime.lagompbRuntime
      )
    )
}
