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
import com.namely.chiefofstate.ServiceBootstrapper.{StartCommand, StartMigration}
import com.typesafe.config.Config
import org.slf4j.{Logger, LoggerFactory}

object StartNodeBehaviour {
  final val log: Logger = LoggerFactory.getLogger(getClass)

  def apply(config: Config): Behavior[NotUsed] = {
    Behaviors.setup { context =>

      val cluster: Cluster = Cluster(context.system)
      context.log.info(s"starting node with roles: ${cluster.selfMember.roles}")

      // Start the akka cluster management tool
      AkkaManagement(context.system).start()
      // start the cluster boostrap
      ClusterBootstrap(context.system).start()

      // initialize the service bootstrapper
      val bootstrapper: ActorRef[StartCommand] =
        context.spawn(
          Behaviors.supervise(ServiceBootstrapper(config)).onFailure[Exception](SupervisorStrategy.restart),
          "CosServiceBootstrapper"
        )

      // initialise the migration runner in a singleton
      val migrator: ActorRef[StartCommand] =
        ClusterSingleton(context.system).init(
          SingletonActor(
            Behaviors
              .supervise(ServiceMigrationRunner(config))
              .onFailure[Exception](SupervisorStrategy.stop),
            "CosServiceMigrationRunner"
          )
        )

      // tell the migrator to kickstart
      migrator ! StartMigration(bootstrapper)

      // let us watch both actors to handle any on them termination
      context.watch(migrator)
      context.watch(bootstrapper)

      // let us handle the Terminated message received
      Behaviors.receiveSignal[NotUsed] { case (context, Terminated(ref)) =>
        context.log.info("Actor stopped: {}", ref.path.name)

        // whenever any of the key starters (ServiceBootstrapper or ServiceMigrationRunner) stop
        // we need to panic here and halt the whole system
        throw new RuntimeException("unable to boot ChiefOfState properly....")
      }

      Behaviors.empty
    }
  }
}
