/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.readside

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.{ ProjectionBehavior, ProjectionId }
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.jdbc.scaladsl.JdbcProjection
import akka.projection.scaladsl.{ ExactlyOnceProjection, SourceProvider }
import com.namely.protobuf.chiefofstate.v1.persistence.EventWrapper
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.{ Logger, LoggerFactory }

/**
 * Read side processor creates a sharded daemon process for handling
 * akka projections read sides
 *
 * @param actorSystem actor system
 * @param processorId ID for this read side
 * @param dataSource hikari data source to connect through
 * @param remoteReadProcessor forwards messages remotely via gRPC
 * @param numShards number of shards for projections/tags
 */
private[readside] class ReadSideProcessor(
    actorSystem: ActorSystem[_],
    val processorId: String,
    val dataSource: HikariDataSource,
    remoteReadProcessor: RemoteReadSideProcessor,
    val numShards: Int) {
  final val log: Logger = LoggerFactory.getLogger(getClass)

  implicit val sys: ActorSystem[_] = actorSystem

  /**
   * Initialize the projection to start fetching the events that are emitted
   */
  def init(): Unit = {
    ShardedDaemonProcess(actorSystem).init[ProjectionBehavior.Command](
      name = processorId,
      numberOfInstances = numShards,
      behaviorFactory = shardNumber => ProjectionBehavior(jdbcProjection(shardNumber.toString)),
      settings = ShardedDaemonProcessSettings(actorSystem),
      stopMessage = Some(ProjectionBehavior.Stop))
  }

  private[readside] def jdbcProjection(tagName: String): ExactlyOnceProjection[Offset, EventEnvelope[EventWrapper]] = {
    JdbcProjection.exactlyOnce(
      projectionId = ProjectionId(processorId, tagName),
      sourceProvider = sourceProvider(tagName),
      // defines a session factory that returns a jdbc
      // session connected to the hikari pool
      sessionFactory = () => new ReadSideJdbcSession(dataSource.getConnection()),
      handler = () => new ReadSideJdbcHandler(tagName, processorId, remoteReadProcessor))

  }

  /**
   * Set the Event Sourced Provider per tag
   *
   * @param tag the event tag
   * @return the event sourced provider
   */
  protected def sourceProvider(tag: String): SourceProvider[Offset, EventEnvelope[EventWrapper]] = {
    EventSourcedProvider.eventsByTag[EventWrapper](actorSystem, readJournalPluginId = JdbcReadJournal.Identifier, tag)
  }
}
