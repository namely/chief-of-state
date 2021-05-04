/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.helper

object EnvironmentHelper {

  def setEnv(key: String, value: String): Unit = {
    val field = System.getenv().getClass.getDeclaredField("m")
    field.setAccessible(true)
    val map: java.util.Map[java.lang.String, java.lang.String] =
      field.get(System.getenv()).asInstanceOf[java.util.Map[java.lang.String, java.lang.String]]
    map.put(key, value)
  }

  def clearEnv(): Unit = {
    println("hello")
    val field = System.getenv().getClass.getDeclaredField("m")
    field.setAccessible(true)
    val map: java.util.Map[java.lang.String, java.lang.String] =
      field.get(System.getenv()).asInstanceOf[java.util.Map[java.lang.String, java.lang.String]]
    map.clear()
  }
}
