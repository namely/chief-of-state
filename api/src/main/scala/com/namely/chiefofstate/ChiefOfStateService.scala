package com.namely.chiefofstate

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{Descriptor, ServiceCall}
import com.namely.lagom.NamelyService
import com.lightbend.lagom.scaladsl.api.Service.restCall

trait ChiefOfStateService extends NamelyService {

  override val serviceName: String = "chiefOfState"

  def handleCommand(): ServiceCall[NotUsed, String]

  override val routes: Seq[Descriptor.Call[_, _]] = Seq(
    restCall(Method.GET, "/api/v1/sidecar", handleCommand _)
  )
}
