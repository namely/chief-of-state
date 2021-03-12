/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration.versions.v2

import akka.persistence.{AtomicWrite, PersistentRepr}
import akka.persistence.jdbc.journal.dao.legacy
import com.namely.protobuf.chiefofstate.v1.tests.{AccountDebited, AccountOpened}

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try

object DataFeeds {
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

  def feedLegacyJournal(legacyDao: legacy.ByteArrayJournalDao): Seq[Try[Unit]] = {
    val atomicWrites = persistentReprs.map(repr => AtomicWrite(repr))

    Await.result(legacyDao.asyncWriteMessages(atomicWrites), Duration.Inf)
  }
}
