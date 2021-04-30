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
import javax.sql.DataSource
import org.slf4j.{ Logger, LoggerFactory }
import akka.actor.typed.Behavior
import javax.sql.DataSource

/**
 * Read side processor creates a sharded daemon process for handling
 * akka projections read sides
 *
 * @param actorSystem actor system
 * @param processorId ID for this read side
 * @param dataSource hikari data source to connect through
 * @param readSideHandler the handler implementation for the read side
 * @param numShards number of shards for projections/tags
 */
private[readside] class ReadSideProjection(
    actorSystem: ActorSystem[_],
    val processorId: String,
    val dataSource: DataSource,
    readSideHandler: ReadSideHandler,
    val numShards: Int) {
  final val log: Logger = LoggerFactory.getLogger(getClass)

  implicit val sys: ActorSystem[_] = actorSystem

  /**
   * Initialize the projection to start fetching the events that are emitted
   */
  def start(): Unit = {
    ShardedDaemonProcess(actorSystem).init[ProjectionBehavior.Command](
      name = processorId,
      numberOfInstances = numShards,
      behaviorFactory = shardNumber => jdbcProjection(shardNumber.toString),
      settings = ShardedDaemonProcessSettings(actorSystem),
      stopMessage = Some(ProjectionBehavior.Stop))
  }

  /**
   * creates a jdbc projection behavior
   *
   * @param tagName the name of the tag
   * @return a behavior for this projection
   */
  private[readside] def jdbcProjection(tagName: String): Behavior[ProjectionBehavior.Command] = {
    val projection = JdbcProjection.exactlyOnce(
      projectionId = ProjectionId(processorId, tagName),
      sourceProvider = ReadSideProjection.sourceProvider(actorSystem, tagName),
      // defines a session factory that returns a jdbc
      // session connected to the hikari pool
      sessionFactory = () => new ReadSideJdbcSession(dataSource.getConnection()),
      handler = () => new ReadSideJdbcHandler(tagName, processorId, readSideHandler))

    ProjectionBehavior(projection)
  }
}

private[readside] object ReadSideProjection {

  /**
   * Set the Event Sourced Provider per tag
   *
   * @param system the actor system
   * @param tag the event tag
   * @return the event sourced provider
   */
  private[readside] def sourceProvider(
      system: ActorSystem[_],
      tag: String): SourceProvider[Offset, EventEnvelope[EventWrapper]] = {
    EventSourcedProvider.eventsByTag[EventWrapper](system, readJournalPluginId = JdbcReadJournal.Identifier, tag)
  }
}
