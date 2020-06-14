package com.namely.chiefofstate.api

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.{Descriptor, ServiceCall}
import com.lightbend.lagom.scaladsl.api.Service.restCall
import com.lightbend.lagom.scaladsl.api.transport.Method
import lagompb.LagompbService

trait ChiefOfStateService extends LagompbService {

  def handleCommand(): ServiceCall[NotUsed, String]

  override val routes: Seq[Descriptor.Call[_, _]] = Seq(restCall(Method.GET, "/", handleCommand _))
}
