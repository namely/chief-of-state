/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.readside

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.persistence.jdbc.query.javadsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.{ProjectionBehavior, ProjectionId}
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.scaladsl.{ExactlyOnceProjection, SourceProvider}
import akka.projection.slick.SlickProjection
import com.namely.chiefofstate.config.CosConfig
import com.namely.protobuf.chiefofstate.v1.persistence.EventWrapper
import org.slf4j.{Logger, LoggerFactory}
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import com.namely.chiefofstate.AggregateRoot
import akka.projection.jdbc.scaladsl.JdbcProjection
import akka.projection.jdbc.JdbcSession
import akka.projection.jdbc.scaladsl.JdbcHandler
import scala.concurrent.duration.FiniteDuration
import com.typesafe.config.Config

/**
 * ReadSide processor actor
 *
 * @param actorSystem the actor system
 * @param processorId the unique id of the processor
 * @param remoteReadProcessor the actual events processor
 * @param cosConfig the main application config
 */
class ReadSideProcessor(
  actorSystem: ActorSystem[_],
  val processorId: String,
  remoteReadProcessor: RemoteReadSideProcessor,
  cosConfig: CosConfig
) {
  final val log: Logger = LoggerFactory.getLogger(getClass)

  implicit val sys: ActorSystem[_] = actorSystem

  // val databaseConfig: DatabaseConfig[PostgresProfile] =
  //   DatabaseConfig.forConfig("akka.projection.slick", actorSystem.settings.config)

  val baseTag: String = cosConfig.eventsConfig.eventTag

  /**
   * Initialize the projection to start fetching the events that are emitted
   */
  def init(): Unit = {
    ShardedDaemonProcess(actorSystem).init[ProjectionBehavior.Command](
      name = processorId,
      numberOfInstances = AggregateRoot.tags(cosConfig.eventsConfig).size,
      behaviorFactory = n => ProjectionBehavior(jdbcProjection(s"$baseTag$n")),
      settings = ShardedDaemonProcessSettings(actorSystem),
      stopMessage = Some(ProjectionBehavior.Stop)
    )
  }

  // /**
  //  * Build the projection instance based upon the event tag
  //  *
  //  * @param tagName the event tag
  //  * @return the projection instance
  //  */
  // protected def slickProjection(tagName: String): ExactlyOnceProjection[Offset, EventEnvelope[EventWrapper]] = {
  //   SlickProjection
  //     .exactlyOnce(
  //       projectionId = ProjectionId(processorId, tagName),
  //       sourceProvider(tagName),
  //       databaseConfig,
  //       handler = () => new ReadSideSlickHandler(tagName, processorId, remoteReadProcessor)
  //     )
  // }

  private[readside] def jdbcProjection(tagName: String): ExactlyOnceProjection[Offset, EventEnvelope[EventWrapper]] = {
    // FIXME: perhaps move this to a constructor for the JdbcSession
    val cfg: Config = actorSystem.settings.config.getConfig("jdbc-default")
    val jdbcUrl: String = cfg.getString("url")
    val username: String = cfg.getString("user")
    val password: String = cfg.getString("password")

    log.info(s"creating jdbcProjection for $jdbcUrl, $username")

    val sessionFactory: () => JdbcSession = () =>
      new ReadSideJdbcSession(
        jdbcUrl = jdbcUrl,
        username = username,
        password = password
      )

    JdbcProjection
      .exactlyOnce(
        projectionId = ProjectionId(processorId, tagName),
        sourceProvider = sourceProvider(tagName),
        sessionFactory = sessionFactory,
        handler = () => new ReadSideJdbcHandler(tagName, processorId, remoteReadProcessor)
      )
    // .withRestartBackoff(
    //   minBackoff = FiniteDuration(3L, "seconds"),
    //   maxBackoff = FiniteDuration(30L, "seconds"),
    //   randomFactor = 0.2,
    //   maxRestarts = -1
    // )

  }

  /**
   * Set the Event Sourced Provider per tag
   *
   * @param tag the event tag
   * @return the event sourced provider
   */
  protected def sourceProvider(tag: String): SourceProvider[Offset, EventEnvelope[EventWrapper]] = {
    EventSourcedProvider
      .eventsByTag[EventWrapper](actorSystem, readJournalPluginId = JdbcReadJournal.Identifier, tag)
  }
}
