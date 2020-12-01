package com.namely.chiefofstate

import com.google.protobuf.any.Any
import com.namely.chiefofstate.config._
import com.namely.chiefofstate.helper.BaseSpec
import com.namely.protobuf.chiefofstate.v1.tests.{Account, AccountOpened}
import com.typesafe.config.{Config, ConfigFactory}

class ProtosValidatorSpec extends BaseSpec {
  "Events and State protos validation" should {
    "pass through successfully when validation is disabled" in {
      val config: Config = ConfigFactory.parseString(s"""
            chiefofstate {
              write-side {
                host = "localhost"
                port = 1000
                enable-protos-validation = false
                states-protos = ""
                events-protos = ""
                propagated-headers = ""
              }
            }
          """)
      val writeSideConfig: WriteSideConfig = WriteSideConfig(config)

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
      val config: Config = ConfigFactory.parseString(s"""
            chiefofstate {
              write-side {
                host = "localhost"
                port = 1000
                enable-protos-validation = true
                states-protos = "chief_of_state.v1.Account"
                events-protos = "chief_of_state.v1.AccountOpened"
                propagated-headers = ""
              }
            }
          """)
      val writeSideConfig: WriteSideConfig = WriteSideConfig(config)

      val eventsAndStateProtosValidation: ProtosValidator =
        ProtosValidator(writeSideConfig)
      val event = AccountOpened()
      val state = Account()
      var isValid = eventsAndStateProtosValidation.validateEvent(Any.pack(event))
      isValid shouldBe true
      isValid = eventsAndStateProtosValidation.validateState(Any.pack(state))
      isValid shouldBe true
    }

    "invalidate events and state proto successfully" in {
      val config: Config = ConfigFactory.parseString(s"""
            chiefofstate {
              write-side {
                host = "localhost"
                port = 1000
                enable-protos-validation = true
                states-protos = ""
                events-protos = ""
                propagated-headers = ""
              }
            }
          """)
      val writeSideConfig: WriteSideConfig = WriteSideConfig(config)

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
      val config: Config = ConfigFactory.parseString(s"""
            chiefofstate {
              write-side {
                host = "localhost"
                port = 1000
                enable-protos-validation = true
                states-protos = ""
                events-protos = ""
                propagated-headers = ""
              }
            }
          """)
      val writeSideConfig: WriteSideConfig = WriteSideConfig(config)

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
