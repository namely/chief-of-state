/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v2

import akka.persistence.{PersistentRepr, SnapshotMetadata}
import akka.persistence.jdbc.journal.dao.legacy
import akka.persistence.jdbc.journal.dao.legacy.JournalRow
import akka.persistence.jdbc.snapshot.dao.legacy.ByteArraySnapshotDao
import akka.serialization.Serialization
import com.google.protobuf.any.Any
import com.namely.protobuf.chiefofstate.v1.persistence.EventWrapper
import com.namely.protobuf.chiefofstate.v1.tests.{Account, AccountDebited, AccountOpened}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._

import java.time.Instant
import java.util.UUID
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration

object Testdata {
  val persistenceIdOne: String = UUID.randomUUID().toString
  val persistenceIdTwo: String = UUID.randomUUID().toString
  val persistenceIdThree: String = UUID.randomUUID().toString
  val persistenceIdFour: String = UUID.randomUUID().toString

  def setJournals(serialization: Serialization): Seq[JournalRow] = {
    Seq(
      JournalRow(
        ordering = 1L,
        deleted = false,
        persistenceId = persistenceIdOne,
        sequenceNumber = 1L,
        message = serialization
          .serialize(
            PersistentRepr(
              payload = EventWrapper().withEvent(
                com.google.protobuf.any.Any.pack(
                  AccountOpened()
                    .withAccountNumber("number-1")
                    .withAccountUuid(persistenceIdOne)
                )
              ),
              sequenceNr = 1L,
              persistenceId = persistenceIdOne,
              deleted = false
            )
          )
          .get,
        tags = Some("chiefofstate-1")
      ),
      JournalRow(
        ordering = 2L,
        deleted = false,
        persistenceId = persistenceIdTwo,
        sequenceNumber = 1L,
        message = serialization
          .serialize(
            PersistentRepr(
              payload = EventWrapper().withEvent(
                Any.pack(
                  AccountOpened()
                    .withAccountNumber("number-2")
                    .withAccountUuid(persistenceIdTwo)
                )
              ),
              sequenceNr = 1L,
              persistenceId = persistenceIdTwo,
              deleted = false
            )
          )
          .get,
        tags = Some("chiefofstate-2")
      ),
      JournalRow(
        ordering = 2L,
        deleted = false,
        persistenceId = persistenceIdTwo,
        sequenceNumber = 2L,
        message = serialization
          .serialize(
            PersistentRepr(
              payload = EventWrapper().withEvent(
                Any.pack(
                  AccountDebited()
                    .withBalance(20)
                    .withAccountUuid(persistenceIdTwo)
                )
              ),
              sequenceNr = 2L,
              persistenceId = persistenceIdTwo,
              deleted = false
            )
          )
          .get,
        tags = Some("chiefofstate-2")
      ),
      JournalRow(
        ordering = 2L,
        deleted = false,
        persistenceId = persistenceIdThree,
        sequenceNumber = 1L,
        message = serialization
          .serialize(
            PersistentRepr(
              payload = EventWrapper().withEvent(
                Any.pack(
                  AccountOpened()
                    .withAccountNumber("number-3")
                    .withAccountUuid(persistenceIdThree)
                )
              ),
              sequenceNr = 1L,
              persistenceId = persistenceIdThree,
              deleted = false
            )
          )
          .get,
        tags = Some("chiefofstate-2")
      ),
      JournalRow(
        ordering = 2L,
        deleted = false,
        persistenceId = persistenceIdFour,
        sequenceNumber = 1L,
        message = serialization
          .serialize(
            PersistentRepr(
              payload = EventWrapper().withEvent(
                Any.pack(
                  AccountOpened()
                    .withAccountNumber("number-4")
                    .withAccountUuid(persistenceIdFour)
                )
              ),
              sequenceNr = 1L,
              persistenceId = persistenceIdFour,
              deleted = false
            )
          )
          .get,
        tags = Some("chiefofstate-2")
      ),
      JournalRow(
        ordering = 2L,
        deleted = false,
        persistenceId = persistenceIdTwo,
        sequenceNumber = 3L,
        message = serialization
          .serialize(
            PersistentRepr(
              payload = EventWrapper().withEvent(
                Any.pack(
                  AccountDebited()
                    .withBalance(20)
                    .withAccountUuid(persistenceIdTwo)
                )
              ),
              sequenceNr = 3L,
              persistenceId = persistenceIdTwo,
              deleted = false
            )
          )
          .get,
        tags = Some("chiefofstate-2")
      )
    )
  }

  val snapshots: Seq[(SnapshotMetadata, Account)] = Seq(
    SnapshotMetadata(
      persistenceId = "some-persistence-id-1",
      sequenceNr = 1L,
      timestamp = Instant.now().getEpochSecond
    ) -> Account.defaultInstance.withAccountUuid("some-persistence-id-1"),
    SnapshotMetadata(
      persistenceId = "some-persistence-id-2",
      sequenceNr = 2L,
      timestamp = Instant.now().getEpochSecond
    ) -> Account.defaultInstance.withAccountUuid("some-persistence-id-2"),
    SnapshotMetadata(
      persistenceId = "some-persistence-id-3",
      sequenceNr = 3L,
      timestamp = Instant.now().getEpochSecond
    ) -> Account.defaultInstance.withAccountUuid("some-persistence-id-3"),
    SnapshotMetadata(
      persistenceId = "some-persistence-id-4",
      sequenceNr = 4L,
      timestamp = Instant.now().getEpochSecond
    ) -> Account.defaultInstance.withAccountUuid("some-persistence-id-4")
  )

  def feedLegacyJournal(serialization: Serialization,
                        journalQueries: legacy.JournalQueries,
                        journalJdbcConfig: DatabaseConfig[JdbcProfile]
  ): Seq[Long] = {
    val action: DBIO[Seq[Long]] =
      journalQueries.JournalTable.returning(journalQueries.JournalTable.map(_.ordering)) ++= setJournals(serialization)
    Await.result(journalJdbcConfig.db.run(action), Duration.Inf)
  }

  def feedLegacySnapshot(legacyDao: ByteArraySnapshotDao)(implicit ec: ExecutionContext): Seq[Unit] = {
    val future = Future.sequence(snapshots.map({ case (metadata, account) =>
      legacyDao.save(metadata, account)
    }))

    Await.result(future, Duration.Inf)
  }
}
