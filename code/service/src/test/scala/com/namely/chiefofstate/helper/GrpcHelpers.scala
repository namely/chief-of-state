package com.namely.chiefofstate.helper

import io.grpc._
import java.io.Closeable
import scala.collection.mutable
import java.util.concurrent.TimeUnit
import io.opentracing.mock.MockTracer
import io.opentracing.util.GlobalTracer

object GrpcHelpers {

  private lazy val mockTracer: MockTracer = {
    val tracer = new MockTracer(MockTracer.Propagator.TEXT_MAP)
    GlobalTracer.registerIfAbsent(tracer)
    tracer
  }

  def getMockTracer(wait: Long = 100): MockTracer = {
    Thread.sleep(wait)
    mockTracer
  }

  def resetMockTracer(): Unit = mockTracer.reset()

  def getHeaders(headers: (String, String)*): Metadata = {
    val metadata: Metadata = new Metadata()
    headers.foreach({
      case (k, v) =>
        metadata.put(
          Metadata.Key.of(k, Metadata.ASCII_STRING_MARSHALLER),
          v
        )
    })
    metadata
  }

  def getStringHeader(headers: Metadata, key: String): String = {
    headers.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER))
  }

  class Closeables() {
    val resources: mutable.ListBuffer[Closeable] = mutable.ListBuffer.empty[Closeable]

    def register[T <: Closeable](closeable: T): T = {
      resources.append(closeable)
      closeable
    }

    def register(server: io.grpc.Server): io.grpc.Server = {
      val closeable = new Closeable {
        def close(): Unit = {
          server.shutdownNow()
          server.awaitTermination(10000, TimeUnit.MILLISECONDS)
        }
      }

      register(closeable)

      server
    }

    def register(channel: ManagedChannel): ManagedChannel = {
      val closeable = new Closeable {
        def close(): Unit = {
          channel.shutdownNow()
          channel.awaitTermination(10000, TimeUnit.MILLISECONDS)
        }
      }

      register(closeable)

      channel
    }

    def closeAll(): Unit = {
      resources.foreach(_.close())
      resources.clear
    }
  }
}
