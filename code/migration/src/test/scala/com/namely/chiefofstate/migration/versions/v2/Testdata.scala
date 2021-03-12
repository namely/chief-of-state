/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v2

import akka.persistence.{AtomicWrite, PersistentRepr, SnapshotMetadata}
import akka.persistence.jdbc.journal.dao.legacy
import akka.persistence.jdbc.snapshot.dao.legacy.ByteArraySnapshotDao
import com.namely.protobuf.chiefofstate.v1.tests.{Account, AccountDebited, AccountOpened}

import java.time.Instant
import java.util.UUID
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.util.Try

object Testdata {
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

  def feedLegacyJournal(legacyDao: legacy.ByteArrayJournalDao): Seq[Try[Unit]] = {
    val atomicWrites = persistentReprs.map(repr => AtomicWrite(repr))

    Await.result(legacyDao.asyncWriteMessages(atomicWrites), Duration.Inf)
  }

  def feedLegacySnapshot(legacyDao: ByteArraySnapshotDao)(implicit ec: ExecutionContext): Seq[Unit] = {
    val future = Future.sequence(snapshots.map({ case (metadata, account) =>
      legacyDao.save(metadata, account)
    }))

    Await.result(future, Duration.Inf)
  }
}
