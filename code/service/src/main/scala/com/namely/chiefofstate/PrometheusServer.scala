package com.namely.chiefofstate

import io.micrometer.prometheus.PrometheusMeterRegistry
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.io.OutputStream
import scala.concurrent.Future
import scala.util.Success
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import scala.util.Try
import scala.concurrent.ExecutionContext

class PrometheusServer(prometheusRegistry: PrometheusMeterRegistry, port: Int, ec: ExecutionContext) {

  import PrometheusServer.logger

  val server: HttpServer = HttpServer.create(new InetSocketAddress(port), 0)
  private var isStarted: Boolean = false

  server.createContext(
    "/prometheus",
    httpExchange => {
      val response: String = prometheusRegistry.scrape()
      httpExchange.sendResponseHeaders(200, response.getBytes().length)

      val os: OutputStream = httpExchange.getResponseBody()

      try {
        os.write(response.getBytes())
      } finally {
        os.close()
      }
    }
  )

  def start(): Unit = {
    implicit val ec = this.ec

    if (!isStarted) {
      logger.info(s"starting PrometheusServer on port $port")
      val serverFuture: Future[Unit] = Future(server.start())
      isStarted = true

      serverFuture.onComplete((result: Try[Unit]) => {
        if (result.isFailure) {
          logger.error("prometheus server failed", result.failed.get)
          isStarted = false
          start()
        }
      })
    }
  }

  def stop(delay: Int = 0): Unit = {
    this.server.stop(delay)
  }
}

object PrometheusServer {
  val logger: Logger = LoggerFactory.getLogger(getClass)

  def apply(prometheusRegistry: PrometheusMeterRegistry, port: Int): PrometheusServer = {
    val ec = ExecutionContext.fromExecutor(
      new java.util.concurrent.ForkJoinPool(1)
    )

    new PrometheusServer(prometheusRegistry, port, ec)
  }
}
