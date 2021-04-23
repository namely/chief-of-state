/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v5

import com.namely.chiefofstate.migration.{ SchemasUtil, Version }
import com.namely.protobuf.chiefofstate.v1.persistence.{ EventWrapper, StateWrapper }
import org.slf4j.{ Logger, LoggerFactory }
import slick.basic.DatabaseConfig
import slick.dbio.DBIO
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._
import V5.log
import scala.util.Try
import slick.jdbc.{ GetResult, ResultSetConcurrency, ResultSetType }
import akka.stream.scaladsl.Source
import scala.concurrent.Future
import akka.actor.typed.ActorSystem
import akka.Done
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import com.namely.protobuf.chiefofstate.plugins.persistedheaders.v1.headers.{
  Header => LegacyHeader,
  Headers => LegacyHeaders
}
import com.namely.protobuf.chiefofstate.v1.common.Header

case class V5(system: ActorSystem[_], journalJdbcConfig: DatabaseConfig[JdbcProfile]) extends Version {
  override def versionNumber: Int = 5

  /**
   * runs header migrations before the commit transaction. these operations
   * are idempotent.
   *
   * @return Success/failure
   */
  override def beforeUpgrade(): Try[Unit] = Try {
    V5.migrateEvents(journalJdbcConfig)(system)
    V5.migrateSnapshots(journalJdbcConfig)(system)
  }

  override def upgrade(): DBIO[Unit] = DBIO.successful {}

  /**
   * creates the latest COS schema if no prior versions found.
   *
   * @return a DBIO that creates the version snapshot
   */
  override def snapshot(): DBIO[Unit] = {
    log.info(s"running snapshot for version #$versionNumber")
    SchemasUtil.createStoreTables(journalJdbcConfig)
    DBIO.successful {}
  }
}

object V5 {
  final val log: Logger = LoggerFactory.getLogger(getClass)

  private val pluginId: String = "persisted_headers.v1"
  private val pageSize: Int = 1000

  /**
   * adds headers to event meta
   *
   * @param dbConfig db config to run
   * @param system actor system
   */
  def migrateEvents(dbConfig: DatabaseConfig[JdbcProfile])(implicit system: ActorSystem[_]): Unit = {
    implicit val rowType1 = GetResult(r => (r.nextLong(), r.nextBytes()))

    val query = sql"""
      select ordering, event_payload
      from event_journal
      order by ordering
    """
      .as[(Long, Array[Byte])]
      .withStatementParameters(
        rsType = ResultSetType.ForwardOnly,
        rsConcurrency = ResultSetConcurrency.ReadOnly,
        fetchSize = pageSize)
      .transactionally

    val pipeline: Future[Done] = Source
      // build source from query
      .fromPublisher(dbConfig.db.stream(query))
      .map({
        case (ordering, bytea) => {
          val eventWrapper: EventWrapper = EventWrapper.parseFrom(bytea)

          // calculate new headers
          val newHeaders = eventWrapper.getMeta.data
            .get(pluginId)
            .map(_.unpack[LegacyHeaders].headers)
            .getOrElse(Seq.empty[LegacyHeader])
            .map(upgradeHeader)
            .filterNot(header => eventWrapper.getMeta.headers.contains(header))

          if (newHeaders.nonEmpty) {
            val newWrapper = eventWrapper.withMeta(eventWrapper.getMeta.addHeaders(newHeaders: _*))
            Some((ordering, newWrapper))
          } else {
            None
          }
        }
      })
      .filter(_.isDefined)
      .map(_.get)
      .map({
        case (ordering, eventWrapper) => {

          val b64 = java.util.Base64.getEncoder().encodeToString(eventWrapper.toByteArray)

          // create sql insert
          sqlu"""
          update event_journal
          set event_payload = decode($b64, 'base64')
          where ordering = $ordering
        """
        }
      })
      .grouped(pageSize)
      .mapAsync(1)(stmts => dbConfig.db.run(DBIO.seq((stmts: _*))))
      .run()

    Await.result(pipeline, Duration.Inf)
  }

  /**
   * adds headers to snapshot
   *
   * @param dbConfig db config to run
   * @param system actor system
   */
  def migrateSnapshots(dbConfig: DatabaseConfig[JdbcProfile])(implicit system: ActorSystem[_]): Unit = {
    implicit val rowType2 = GetResult(r => (r.nextString(), r.nextLong(), r.nextBytes()))

    val query = sql"""
      select persistence_id, sequence_number, snapshot_payload
      from state_snapshot
      order by persistence_id
    """
      .as[(String, Long, Array[Byte])]
      .withStatementParameters(
        rsType = ResultSetType.ForwardOnly,
        rsConcurrency = ResultSetConcurrency.ReadOnly,
        fetchSize = pageSize)
      .transactionally

    val pipeline: Future[Done] = Source
      // build source from query
      .fromPublisher(dbConfig.db.stream(query))
      .map({
        case (ordering, sequenceNumber, bytea) => {
          val stateWrapper: StateWrapper = StateWrapper.parseFrom(bytea)

          // calculate new headers
          val newHeaders = stateWrapper.getMeta.data
            .get(pluginId)
            .map(_.unpack[LegacyHeaders].headers)
            .getOrElse(Seq.empty[LegacyHeader])
            .map(upgradeHeader)
            .filterNot(header => stateWrapper.getMeta.headers.contains(header))

          if (newHeaders.nonEmpty) {
            val newWrapper = stateWrapper.withMeta(stateWrapper.getMeta.addHeaders(newHeaders: _*))
            Some((ordering, sequenceNumber, newWrapper))
          } else {
            None
          }
        }
      })
      .filter(_.isDefined)
      .map(_.get)
      .map({
        case (id: String, sequenceNumber: Long, stateWrapper: StateWrapper) => {

          val b64 = java.util.Base64.getEncoder().encodeToString(stateWrapper.toByteArray)

          // create sql insert
          sqlu"""
          update state_snapshot
          set snapshot_payload = decode($b64, 'base64')
          where persistence_id = $id and sequence_number = $sequenceNumber
        """
        }
      })
      .grouped(pageSize)
      .mapAsync(1)(stmts => dbConfig.db.run(DBIO.seq((stmts: _*))))
      .run()

    Await.result(pipeline, Duration.Inf)
  }

  /**
   * convert legacy header to new header
   *
   * @param header leagcy header
   * @return new header class
   */
  private[chiefofstate] def upgradeHeader(header: LegacyHeader): Header = {
    Header(
      header.key,
      header.value match {
        case LegacyHeader.Value.Empty              => Header.Value.Empty
        case LegacyHeader.Value.StringValue(value) => Header.Value.StringValue(value)
        case LegacyHeader.Value.BytesValue(value)  => Header.Value.BytesValue(value)
      })
  }
}
