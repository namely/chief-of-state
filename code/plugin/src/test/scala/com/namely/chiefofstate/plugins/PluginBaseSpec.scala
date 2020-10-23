package com.namely.chiefofstate.plugins

import com.google.protobuf.wrappers.BoolValue
import org.mockito.Mockito
import org.scalatest.TestSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.util.Try

class PluginBaseSpec
  extends AnyWordSpecLike
    with Matchers
    with TestSuite {

  "PluginBase" should {
    "return the Option of the String packed as a proto Any" in {
      val pluginId: String = "pluginId"
      val bool: Boolean = true
      val anyProto: com.google.protobuf.any.Any = PluginBaseSpecCompanion.makeAny(bool)

      val mockPluginBase: PluginBase = Mockito.mock(classOf[PluginBase])
      Mockito.when(mockPluginBase.pluginId).thenReturn(pluginId)
      Mockito.when(mockPluginBase.makeMeta(bool)).thenReturn(Some(anyProto))
      Mockito.when(mockPluginBase.run(bool)).thenCallRealMethod()

      val result: Try[Map[String, com.google.protobuf.any.Any]] = mockPluginBase.run(bool)

      result.isSuccess should be (true)
      result.get.keySet.size should be (1)
      result.get.keySet.contains(pluginId) should be (true)
      result.get(pluginId).value should be (PluginBaseSpecCompanion.makeAny(bool).value)
    }

    "return None" in {
      val mockPluginBase: PluginBase = Mockito.mock(classOf[PluginBase])
      Mockito.when(mockPluginBase.makeMeta()).thenReturn(None)
      Mockito.when(mockPluginBase.run()).thenCallRealMethod()

      val result: Try[Map[String, com.google.protobuf.any.Any]] = mockPluginBase.run()

      result.isSuccess should be (true)
      result.get.keySet.size should be (0)
    }

    "return a failure" in {
      val mockPluginBase: PluginBase = Mockito.mock(classOf[PluginBase])
      Mockito.when(mockPluginBase.makeMeta()).thenThrow(new RuntimeException("test"))
      Mockito.when(mockPluginBase.run()).thenCallRealMethod()
      mockPluginBase.run().isFailure should be (true)
    }
  }
}

object PluginBaseSpecCompanion {
  def makeAny(b: Boolean): com.google.protobuf.any.Any = {
    val boolValue: BoolValue = BoolValue(b)
    com.google.protobuf.any.Any.pack(boolValue)
  }
}
