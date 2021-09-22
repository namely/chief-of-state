/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

import com.google.protobuf.any.Any
import com.namely.chiefofstate.config._
import com.namely.chiefofstate.helper.BaseSpec
import com.namely.protobuf.chiefofstate.v1.tests.{ Account, AccountOpened }

class ProtosValidatorSpec extends BaseSpec {

  val sharedConfig: WriteSideConfig = WriteSideConfig(
    host = "localhost",
    port = 1000,
    useTls = false,
    enableProtoValidation = false,
    eventsProtos = Seq(),
    statesProtos = Seq(),
    propagatedHeaders = Seq(),
    persistedHeaders = Seq())

  "Events and State protos validation" should {
    "pass through successfully when validation is disabled" in {
      val writeSideConfig = sharedConfig.copy(enableProtoValidation = false)

      val eventsAndStateProtosValidation: ProtosValidator =
        ProtosValidator(writeSideConfig)

      val event = AccountOpened()
      val state = Account()
      var isValid = eventsAndStateProtosValidation.validateEvent(Any.pack(event))
      isValid shouldBe true
      isValid = eventsAndStateProtosValidation.validateState(Any.pack(state))
      isValid shouldBe true
    }

    "validate events and state proto successfully" in {
      val event = AccountOpened()
      val state = Account()

      val stateUrl = state.companion.scalaDescriptor.fullName
      val eventUrl = event.companion.scalaDescriptor.fullName

      val writeSideConfig =
        sharedConfig.copy(enableProtoValidation = true, eventsProtos = Seq(eventUrl), statesProtos = Seq(stateUrl))

      val eventsAndStateProtosValidation: ProtosValidator =
        ProtosValidator(writeSideConfig)

      var isValid = eventsAndStateProtosValidation.validateEvent(Any.pack(event))
      isValid shouldBe true
      isValid = eventsAndStateProtosValidation.validateState(Any.pack(state))
      isValid shouldBe true
    }

    "invalidate events and state proto successfully" in {
      val writeSideConfig = sharedConfig.copy(enableProtoValidation = true, eventsProtos = Seq(), statesProtos = Seq())

      val eventsAndStateProtosValidation: ProtosValidator =
        ProtosValidator(writeSideConfig)
      val event = AccountOpened()
      val state = Account()
      var isValid = eventsAndStateProtosValidation.validateEvent(Any.pack(event))
      isValid shouldBe false
      isValid = eventsAndStateProtosValidation.validateState(Any.pack(state))
      isValid shouldBe false
    }

    "throws when an event or state is invalid" in {
      val writeSideConfig = sharedConfig.copy(enableProtoValidation = true, eventsProtos = Seq(), statesProtos = Seq())

      val eventsAndStateProtosValidation: ProtosValidator =
        ProtosValidator(writeSideConfig)

      val event: AccountOpened = AccountOpened()
      val state: Account = Account()

      assertThrows[IllegalArgumentException] {
        eventsAndStateProtosValidation.requireValidEvent(Any.pack(event))
      }

      assertThrows[IllegalArgumentException] {
        eventsAndStateProtosValidation.requireValidState(Any.pack(state))
      }
    }

  }
}
