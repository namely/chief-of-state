package com.namely.chiefofstate.helper

import com.namely.protobuf.chiefofstate.test.ping_service._
import scala.concurrent.Future
import io.grpc.Context
import com.namely.chiefofstate.interceptors.GrpcHeadersInterceptor
import scala.collection.mutable

class PingServiceImpl() extends PingServiceGrpc.PingService {

  val manualInterceptors: mutable.ListBuffer[Ping => Unit] = new mutable.ListBuffer()

  def send(request: Ping): Future[Pong] = {
    manualInterceptors.foreach(f => f(request))

    val response = Pong()
      .withMsg(request.msg)

    Future.successful(response)
  }

  def registerInterceptor(f: Ping => Unit): Unit = {
    manualInterceptors.append(f)
  }
}
