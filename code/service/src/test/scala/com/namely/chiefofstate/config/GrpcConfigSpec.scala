/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.config

import com.namely.chiefofstate.helper.BaseSpec
import com.typesafe.config.{ Config, ConfigException, ConfigFactory }

class GrpcConfigSpec extends BaseSpec {
  "Loading gRPC config" should {
    "be successful when all settings are done" in {
      val config: Config = ConfigFactory.parseString(s"""
            chiefofstate {
              grpc {
                client {
                  deadline-timeout = 100
                }
                server {
                  address = "0.0.0.0"
                  port = 9000
                }
              }
            }
          """)

      noException shouldBe thrownBy(GrpcConfig(config))
    }
  }

  "fail when settings are missing or having invalid names" in {
    val config: Config = ConfigFactory.parseString(s"""
            chiefofstate {
              grpc {
                client {
                  deadline-timeout = 100
                }
                # server {
                #  port = 9000
                # }
              }
            }
          """)
    an[ConfigException] shouldBe thrownBy(GrpcConfig(config))
  }
}
