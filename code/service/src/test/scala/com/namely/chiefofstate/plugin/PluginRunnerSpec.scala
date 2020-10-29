package com.namely.chiefofstate.plugin

import com.google.protobuf.wrappers.StringValue
import com.namely.chiefofstate.test.helpers.TestSpec
import com.namely.protobuf.chiefofstate.v1.service.ProcessCommandRequest
import io.grpc.Metadata
import PluginRunner.PluginBaseImplicits

import scala.util.Try

class PluginRunnerSpec extends TestSpec {

  "PluginBase" should {
    val foo: String = "foo"
    val pluginId: String = "pluginId"

    val processCommandRequest: ProcessCommandRequest = ProcessCommandRequest.defaultInstance

    val metadataKey: Metadata.Key[String] = Metadata.Key.of(foo, Metadata.ASCII_STRING_MARSHALLER)
    val metadata: Metadata = new Metadata()
    metadata.put(metadataKey, foo)

    "return the Option of the String packed as a proto Any" in {

      val anyProto: com.google.protobuf.any.Any = com.google.protobuf.any.Any.pack(StringValue(foo))

      val mockPluginBase: PluginBase = mock[PluginBase]
      (mockPluginBase.pluginId _).expects().returning(pluginId)
      (mockPluginBase.makeAny _).expects(processCommandRequest, metadata).returning(Some(anyProto))

      val result: Try[Map[String, com.google.protobuf.any.Any]] = mockPluginBase.run(processCommandRequest, metadata)

      result.isSuccess should be (true)
      result.get.keySet.size should be (1)
      result.get.keySet.contains(pluginId) should be (true)
      result.get(pluginId).unpack[StringValue] should be (StringValue(foo))
    }

    "return None" in {
      val mockPluginBase: PluginBase = mock[PluginBase]
      (mockPluginBase.makeAny _).expects(processCommandRequest, metadata).returning(None)

      val result: Try[Map[String, com.google.protobuf.any.Any]] = mockPluginBase.run(processCommandRequest, metadata)

      result.isSuccess should be (true)
      result.get.keySet.size should be (0)
    }

    "return a failure" in {
      val mockPluginBase: PluginBase = mock[PluginBase]
      (mockPluginBase.makeAny _).expects(processCommandRequest, metadata).throws(new RuntimeException("test"))
      mockPluginBase.run(processCommandRequest, metadata).isFailure should be (true)
    }
  }
}
