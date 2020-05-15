package com.namely.chiefofstate.api

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.Service.restCall
import com.lightbend.lagom.scaladsl.api.Descriptor
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.namely.lagom.NamelyService

trait ChiefOfStateService extends NamelyService {

  override val serviceName: String = "chiefOfState"

  def handleCommand(): ServiceCall[NotUsed, String]

  override val routes: Seq[Descriptor.Call[_, _]] = Seq(
    restCall(Method.GET, "/api/v1/sidecar", handleCommand _)
  )
}
