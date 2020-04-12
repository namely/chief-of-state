package com.namely.chiefofstate

import com.lightbend.lagom.scaladsl.akka.discovery.AkkaDiscoveryComponents
import com.lightbend.lagom.scaladsl.api.Descriptor
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.server.{LagomApplication, LagomApplicationContext, LagomApplicationLoader, LagomServer}
import com.namely.lagom.{NamelyAggregate, NamelyLagomApplication}
import com.softwaremill.macwire.wire

abstract class SidecarApplication(context: LagomApplicationContext) extends NamelyLagomApplication(context) {

  override def aggregateRoot: NamelyAggregate[_] = SidecarAggregate

  override def server: LagomServer =
    serverFor[ChiefOfStateService](wire[SidecarServiceImpl])
      .additionalRouter(wire[SidecarGrpcServiceImpl])
}

class SidecarApplicationLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new SidecarApplication(context) with AkkaDiscoveryComponents

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new SidecarApplication(context) with LagomDevModeComponents

  override def describeService: Option[Descriptor] = Some(readDescriptor[ChiefOfStateService])
}
