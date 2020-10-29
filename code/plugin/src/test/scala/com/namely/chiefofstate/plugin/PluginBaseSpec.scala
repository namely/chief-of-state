package com.namely.chiefofstate.plugin

import com.google.protobuf.ByteString
import com.google.protobuf.wrappers.StringValue
import com.namely.protobuf.chiefofstate.v1.service.ProcessCommandRequest
import io.grpc.Metadata
import org.mockito.Mockito
import org.scalamock.scalatest.MockFactory
import org.scalatest.TestSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.util.Try

class PluginBaseSpec
  extends AnyWordSpecLike
    with Matchers
    with TestSuite
    with MockFactory {

  "PluginBase" should {
    val foo: String = "foo"
    val pluginId: String = "pluginId"

    val processCommandRequest: ProcessCommandRequest = ProcessCommandRequest.defaultInstance

    val metadataKey: Metadata.Key[String] = Metadata.Key.of(foo, Metadata.ASCII_STRING_MARSHALLER)
    val metadata: Metadata = new Metadata()
    metadata.put(metadataKey, foo)

    "return the Option of the String packed as a proto Any" in {

      val anyProto: com.google.protobuf.any.Any = com.google.protobuf.any.Any.pack(StringValue(foo))

      val mockPluginBase: PluginBase = Mockito.mock(classOf[PluginBase])
      Mockito.when(mockPluginBase.pluginId).thenReturn(pluginId)
      Mockito.when(mockPluginBase.makeAny(processCommandRequest, metadata)).thenReturn(Some(anyProto))
      Mockito.when(mockPluginBase.run(processCommandRequest, metadata)).thenCallRealMethod()

      val result: Try[Map[String, com.google.protobuf.any.Any]] = mockPluginBase.run(processCommandRequest, metadata)

      result.isSuccess should be (true)
      result.get.keySet.size should be (1)
      result.get.keySet.contains(pluginId) should be (true)
      result.get(pluginId).unpack[StringValue] should be (StringValue(foo))
    }

    "return None" in {
      val mockPluginBase: PluginBase = Mockito.mock(classOf[PluginBase])
      Mockito.when(mockPluginBase.makeAny(processCommandRequest, metadata)).thenReturn(None)
      Mockito.when(mockPluginBase.run(processCommandRequest, metadata)).thenCallRealMethod()

      val result: Try[Map[String, com.google.protobuf.any.Any]] = mockPluginBase.run(processCommandRequest, metadata)

      result.isSuccess should be (true)
      result.get.keySet.size should be (0)
    }

    "return a failure" in {
      val mockPluginBase: PluginBase = Mockito.mock(classOf[PluginBase])
      Mockito.when(mockPluginBase.makeAny(processCommandRequest, metadata)).thenThrow(new RuntimeException("test"))
      Mockito.when(mockPluginBase.run(processCommandRequest, metadata)).thenCallRealMethod()
      mockPluginBase.run(processCommandRequest, metadata).isFailure should be (true)
    }
  }
}
