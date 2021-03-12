/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v2

import akka.persistence.{AtomicWrite, PersistentRepr}
import akka.persistence.jdbc.journal.dao.legacy
import akka.persistence.jdbc.journal.dao.legacy.JournalRow
import com.namely.protobuf.chiefofstate.v1.tests.{AccountDebited, AccountOpened}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try

object Seeding {
  val persistenceIdOne: String = UUID.randomUUID().toString
  val persistenceIdTwo: String = UUID.randomUUID().toString
  val persistenceIdThree: String = UUID.randomUUID().toString
  val persistenceIdFour: String = UUID.randomUUID().toString

  val persistentReprs: Seq[PersistentRepr] = {
    Seq(
      PersistentRepr(
        payload = AccountOpened()
          .withAccountNumber("number-1")
          .withAccountUuid(persistenceIdOne)
          .toByteArray,
        sequenceNr = 1L,
        persistenceId = persistenceIdOne
      ),
      PersistentRepr(
        payload = AccountOpened()
          .withAccountNumber("number-2")
          .withAccountUuid(persistenceIdTwo)
          .toByteArray,
        sequenceNr = 1L,
        persistenceId = persistenceIdTwo
      ),
      PersistentRepr(
        payload = AccountDebited()
          .withBalance(20)
          .withAccountUuid(persistenceIdTwo)
          .toByteArray,
        sequenceNr = 2L,
        persistenceId = persistenceIdTwo
      ),
      PersistentRepr(
        payload = AccountOpened()
          .withAccountNumber("number-3")
          .withAccountUuid(persistenceIdThree)
          .toByteArray,
        sequenceNr = 1L,
        persistenceId = persistenceIdThree
      ),
      PersistentRepr(
        payload = AccountOpened()
          .withAccountNumber("number-4")
          .withAccountUuid(persistenceIdFour)
          .toByteArray,
        sequenceNr = 1L,
        persistenceId = persistenceIdFour
      ),
      PersistentRepr(
        payload = AccountDebited()
          .withBalance(20)
          .withAccountUuid(persistenceIdTwo)
          .toByteArray,
        sequenceNr = 3L,
        persistenceId = persistenceIdTwo
      )
    )
  }

  def insertNonSerializedDataIntoLegacy(
    journalJdbcConfig: DatabaseConfig[JdbcProfile],
    legacyJournalQueries: legacy.JournalQueries
  ): Long = {

    val data: Seq[JournalRow] = Seq(
      JournalRow(
        1L,
        false,
        "some-persistence-id-1",
        2L,
        AccountOpened()
          .withAccountNumber("number-1")
          .withAccountUuid("some-persistence-id-1")
          .toByteArray,
        Some("chiefofstate0")
      ),
      JournalRow(
        2L,
        false,
        "some-persistence-id-2",
        4L,
        AccountOpened()
          .withAccountNumber("number-2")
          .withAccountUuid("some-persistence-id-2")
          .toByteArray,
        Some("chiefofstate1")
      ),
      JournalRow(
        3L,
        false,
        "some-persistence-id-3",
        12L,
        AccountOpened()
          .withAccountNumber("number-3")
          .withAccountUuid("some-persistence-id-3")
          .toByteArray,
        Some("chiefofstate0")
      ),
      JournalRow(
        4L,
        false,
        "some-persistence-id-0",
        6L,
        AccountOpened()
          .withAccountNumber("number-0")
          .withAccountUuid("some-persistence-id-0")
          .toByteArray,
        Some("chiefofstate0")
      ),
      JournalRow(
        5L,
        false,
        "some-persistence-id-0",
        8L,
        AccountDebited()
          .withBalance(10)
          .withAccountUuid("some-persistence-id-0")
          .toByteArray,
        Some("chiefofstate2")
      ),
      JournalRow(
        6L,
        false,
        "some-persistence-id-0",
        20L,
        AccountDebited()
          .withBalance(20)
          .withAccountUuid("some-persistence-id-0")
          .toByteArray,
        Some("chiefofstate2")
      ),
      JournalRow(
        7L,
        false,
        "some-persistence-id-3",
        2L,
        AccountDebited()
          .withBalance(20)
          .withAccountUuid("some-persistence-id-3")
          .toByteArray,
        Some("chiefofstate3")
      ),
      JournalRow(
        8L,
        false,
        "some-persistence-id-2",
        2L,
        AccountDebited()
          .withBalance(20)
          .withAccountUuid("some-persistence-id-2")
          .toByteArray,
        Some("chiefofstate0")
      )
    )

    val inserts = legacyJournalQueries.JournalTable
      .returning(legacyJournalQueries.JournalTable.map(_.ordering)) ++= data

    Await.result(journalJdbcConfig.db.run(inserts), Duration.Inf).length
  }

  def seedLegacyJournal(legacyDao: legacy.ByteArrayJournalDao): Seq[Try[Unit]] = {
    val atomicWrites = persistentReprs.map(repr => AtomicWrite(repr))

    Await.result(legacyDao.asyncWriteMessages(atomicWrites), Duration.Inf)
  }
}
