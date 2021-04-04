/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.typed.{Cluster, ClusterSingleton, SingletonActor}
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.NotUsed
import com.namely.chiefofstate.serialization.{MessageWithActorRef, ScalaMessage}
import com.namely.protobuf.chiefofstate.v1.internal.DoMigration
import com.typesafe.config.Config
import org.slf4j.{Logger, LoggerFactory}

object StartNodeBehaviour {
  final val log: Logger = LoggerFactory.getLogger(getClass)
  val COS_MIGRATION_RUNNER = "CosServiceMigrationRunner"
  val COS_SERVICE_BOOTSTRAPPER = "CosServiceBootstrapper"

  def apply(config: Config): Behavior[NotUsed] = {
    Behaviors.setup { context =>

      val cluster: Cluster = Cluster(context.system)
      context.log.info(s"starting node with roles: ${cluster.selfMember.roles}")

      // Start the akka cluster management tool
      AkkaManagement(context.system).start()
      // start the cluster boostrap
      ClusterBootstrap(context.system).start()

      // initialize the service bootstrapper
      val bootstrapper: ActorRef[scalapb.GeneratedMessage] =
        context.spawn(
          Behaviors.supervise(ServiceBootstrapper(config)).onFailure[Exception](SupervisorStrategy.restart),
          COS_SERVICE_BOOTSTRAPPER
        )

      // initialise the migration runner in a singleton
      val migrator: ActorRef[ScalaMessage] =
        ClusterSingleton(context.system).init(
          SingletonActor(
            Behaviors
              .supervise(ServiceMigrationRunner(config))
              .onFailure[Exception](SupervisorStrategy.stop),
            COS_MIGRATION_RUNNER
          )
        )

      // tell the migrator to kickstart
      migrator ! MessageWithActorRef(DoMigration.defaultInstance, bootstrapper)

      // let us watch both actors to handle any on them termination
      context.watch(migrator)
      context.watch(bootstrapper)

      // let us handle the Terminated message received
      Behaviors.receiveSignal[NotUsed] { case (context, Terminated(ref)) =>
        val actorName = ref.path.name
        context.log.info("Actor stopped: {}", actorName)
        // whenever any of the key starters (ServiceBootstrapper or ServiceMigrationRunner) stop
        // we need to panic here and halt the whole system
        throw new RuntimeException("unable to boot ChiefOfState properly....")
      }

      Behaviors.empty
    }
  }
}
