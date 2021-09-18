/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v2

import akka.persistence.jdbc.journal.dao.legacy
import akka.persistence.jdbc.snapshot.dao.legacy.ByteArraySnapshotDao
import akka.persistence.{ PersistentRepr, SnapshotMetadata }
import akka.serialization.Serialization
import com.namely.protobuf.chiefofstate.v1.persistence.EventWrapper
import com.namely.protobuf.chiefofstate.v1.tests.{ Account, AccountOpened }
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future }

object Seed {
  private def journalRows(serialization: Serialization, numRows: Int): Seq[legacy.JournalRow] = {
    (1 to numRows).foldLeft(Seq.empty[legacy.JournalRow])((s, i) => {
      val persistenceId: String = UUID.randomUUID.toString
      s :+ legacy.JournalRow(
        ordering = i,
        deleted = false,
        persistenceId = persistenceId,
        sequenceNumber = i,
        message = serialization
          .serialize(PersistentRepr(
            payload = EventWrapper().withEvent(com.google.protobuf.any.Any
              .pack(AccountOpened().withAccountNumber(s"account-number-$i").withAccountUuid(s"account-id-$i"))),
            sequenceNr = i,
            persistenceId = persistenceId,
            deleted = false))
          .getOrElse(Seq.empty[Byte].toArray), // to avoid scalastyle yelling
        tags = Some(s"tag-$i"))
    })
  }

  private def snapshotRows(numRows: Int): Seq[(SnapshotMetadata, Account)] = {
    (1 to numRows).foldLeft(Seq.empty[(SnapshotMetadata, Account)])((s, i) => {
      val persistenceId = UUID.randomUUID.toString
      s :+ SnapshotMetadata(
        persistenceId = persistenceId,
        sequenceNr = i,
        timestamp = Instant.now().getEpochSecond) -> Account.defaultInstance.withAccountUuid(persistenceId)
    })
  }

  def legacyJournal(
      serialization: Serialization,
      journalQueries: legacy.JournalQueries,
      journalJdbcConfig: DatabaseConfig[JdbcProfile],
      numRows: Int = 6): Seq[Long] = {
    val action: DBIO[Seq[Long]] =
      journalQueries.JournalTable
        .returning(journalQueries.JournalTable.map(_.ordering)) ++= journalRows(serialization, numRows)
    Await.result(journalJdbcConfig.db.run(action), Duration.Inf)
  }

  def legacySnapshot(legacyDao: ByteArraySnapshotDao, numRows: Int = 4)(implicit ec: ExecutionContext): Seq[Unit] = {
    val future = Future.sequence(snapshotRows(numRows).map { case (metadata, account) =>
      legacyDao.save(metadata, account)
    })

    Await.result(future, Duration.Inf)
  }
}
