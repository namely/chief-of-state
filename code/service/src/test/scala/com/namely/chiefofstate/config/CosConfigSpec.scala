package com.namely.chiefofstate.config

import com.namely.chiefofstate.helper.BaseSpec
import com.typesafe.config.{Config, ConfigException, ConfigFactory}

class CosConfigSpec extends BaseSpec {
  "Loading main config" should {
    "be successful when all settings are set" in {
      val config: Config = ConfigFactory.parseString(s"""
            akka.cluster.sharding.number-of-shards = 2
            chiefofstate {
             	service-name = "chiefofstate"
              ask-timeout = 5
              snapshot-criteria {
                disable-snapshot = false
                retention-frequency = 100
                retention-number = 2
                delete-events-on-snapshot = false
              }
              events {
                tagname: "cos"
              }
              grpc {
                client {
                  deadline-timeout = 100
                }
                server {
                  address = "0.0.0.0"
                  port = 9000
                }
              }
              write-side {
                host = "localhost"
                port = 1000
                enable-protos-validation = false
                states-protos = ""
                events-protos = ""
                propagated-headers = ""
              }
              read-side {
                create-stores {
                  auto = true
                }
                # set this value to true whenever a readSide config is set
                enabled = false
              }
            }
          """)
      noException shouldBe thrownBy(CosConfig(config))
    }

    "fail when there is either a missing setting or a wrong naming" in {
      val config: Config = ConfigFactory.parseString(s"""
            akka.cluster.sharding.number-of-shards = 2
            chiefofstate {
             	service-name = "chiefofstate"
              ask-timeouts = 5 # wrong naming
              snapshot-criteria {
                disable-snapshot = false
                retention-frequency = 100
                retention-number = 2
                delete-events-on-snapshot = false
              }
              events {
                tagname: "cos"
              }
              grpc {
                client {
                  deadline-timeout = 100
                }
                server {
                  address = "0.0.0.0"
                  port = 9000
                }
              }
              write-side {
                host = "localhost"
                port = 1000
                enable-protos-validation = false
                states-protos = ""
                events-protos = ""
              }
              read-side {
                create-stores {
                  auto = true
                }
                # set this value to true whenever a readSide config is set
                enabled = false
              }
            }
          """)
      an[ConfigException] shouldBe thrownBy(CosConfig(config))
    }
  }
}
