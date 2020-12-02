package com.namely.chiefofstate.common.telemetry

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
import com.typesafe.config.{Config, ConfigException}

class PrometheusServer(prometheusRegistry: PrometheusMeterRegistry, route: String, port: Int, ec: ExecutionContext) {

  import PrometheusServer.logger

  val server: HttpServer = HttpServer.create(new InetSocketAddress(port), 0)
  private var isStarted: Boolean = false

  val finalRoute: String = s"/${route.stripPrefix("/")}"

  server.createContext(
    finalRoute,
    httpExchange => {
      val response: String = prometheusRegistry.scrape()
      httpExchange.sendResponseHeaders(200, response.getBytes().length)
      val os: OutputStream = httpExchange.getResponseBody()
      try os.write(response.getBytes())
      finally os.close()
    }
  )

  def start(): Unit = {
    implicit val ec = this.ec

    if (!isStarted) {
      logger.info(s"starting PrometheusServer on port $port at path $finalRoute")
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

  def apply(prometheusRegistry: PrometheusMeterRegistry, route: String, port: Int): PrometheusServer = {
    val ec = ExecutionContext.fromExecutor(
      new java.util.concurrent.ForkJoinPool(1)
    )

    new PrometheusServer(prometheusRegistry, route, port, ec)
  }

  def apply(prometheusRegistry: PrometheusMeterRegistry, config: Config): PrometheusServer = {
    val port: Int = config.getInt("prometheus.port")
    val route: String = config.getString("prometheus.route").stripPrefix("/")
    apply(prometheusRegistry, route, port)
  }
}
