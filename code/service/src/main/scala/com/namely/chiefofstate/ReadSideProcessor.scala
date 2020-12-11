/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

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

  val offsetStoreDatabaseConfig: DatabaseConfig[PostgresProfile] =
    DatabaseConfig.forConfig("akka.projection.slick", actorSystem.settings.config)

  val baseTag: String = cosConfig.eventsConfig.eventTag

  /**
   * Initialize the projection to start fetching the events that are emitted
   */
  def init(): Unit = {
    // Let us attempt to create the projection store
    if (cosConfig.createDataStores) SlickProjection.createOffsetTableIfNotExists(offsetStoreDatabaseConfig)

    ShardedDaemonProcess(actorSystem).init[ProjectionBehavior.Command](
      name = processorId,
      numberOfInstances = AggregateRoot.tags(cosConfig.eventsConfig).size,
      behaviorFactory = n => ProjectionBehavior(exactlyOnceProjection(s"$baseTag$n")),
      settings = ShardedDaemonProcessSettings(actorSystem),
      stopMessage = Some(ProjectionBehavior.Stop)
    )
  }

  /**
   * Build the projection instance based upon the event tag
   *
   * @param tagName the event tag
   * @return the projection instance
   */
  protected def exactlyOnceProjection(tagName: String): ExactlyOnceProjection[Offset, EventEnvelope[EventWrapper]] = {
    SlickProjection
      .exactlyOnce(
        projectionId = ProjectionId(processorId, tagName),
        sourceProvider(tagName),
        offsetStoreDatabaseConfig,
        handler = () => new ReadSideEventsConsumer(tagName, processorId, remoteReadProcessor)
      )
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
