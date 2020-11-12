package com.namely.chiefofstate.helper

import com.namely.protobuf.chiefofstate.test.ping_service._
import scala.concurrent.Future
import io.grpc.Context
import com.namely.chiefofstate.interceptors.GrpcHeadersInterceptor
import scala.collection.mutable

class PingServiceImpl() extends PingServiceGrpc.PingService {

  val manualInterceptors: mutable.ListBuffer[Ping => Unit] = new mutable.ListBuffer()

  def defaultHandler(request: Ping): Future[Pong] = {
    Future.successful(
      Pong()
        .withMsg(request.msg)
    )
  }

  private var handler: Ping => Future[Pong] = defaultHandler

  def send(request: Ping): Future[Pong] = {
    manualInterceptors.foreach(f => f(request))
    handler(request)
  }

  def registerInterceptor(f: Ping => Unit): Unit = {
    manualInterceptors.append(f)
  }

  def setHandler(handler: Ping => Future[Pong]): Unit = {
    this.handler = handler
  }
}
