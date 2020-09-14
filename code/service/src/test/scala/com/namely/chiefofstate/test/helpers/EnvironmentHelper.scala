package com.namely.chiefofstate.test.helpers

object EnvironmentHelper {

  def setEnv(key: String, value: String): Unit = {
    val field = System.getenv().getClass.getDeclaredField("m")
    field.setAccessible(true)
    val map: java.util.Map[java.lang.String, java.lang.String] =
      field.get(System.getenv()).asInstanceOf[java.util.Map[java.lang.String, java.lang.String]]
    map.put(key, value)
  }

  def clearEnv(): Unit = {
    val field = System.getenv().getClass.getDeclaredField("m")
    field.setAccessible(true)
    val map: java.util.Map[java.lang.String, java.lang.String] =
      field.get(System.getenv()).asInstanceOf[java.util.Map[java.lang.String, java.lang.String]]
    map.clear()
  }
}
