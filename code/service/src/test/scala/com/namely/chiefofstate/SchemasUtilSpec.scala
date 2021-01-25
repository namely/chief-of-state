/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

import com.namely.chiefofstate.config.BootConfig
import com.namely.chiefofstate.helper.{BaseSpec, EnvironmentHelper}
import com.typesafe.config.{Config, ConfigFactory}
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres

class SchemasUtilSpec extends BaseSpec {
  var pg: EmbeddedPostgres = null
  override protected def beforeAll() = {
    val builder: EmbeddedPostgres.Builder = EmbeddedPostgres.builder()
    builder.setPort(25432)
    pg = builder.start()
  }

  override protected def afterAll() = {
    pg.close()
  }

  ".create Journal and Snapshot store with legacy schema" in {
    EnvironmentHelper.setEnv(BootConfig.COS_JOURNAL_USE_LEGACY_SCHEMA, "true")
    val config: Config = ConfigFactory.parseResources("schemas-util.conf").resolve()
    noException shouldBe thrownBy(SchemasUtil.createIfNotExists(config))
  }

  ".create Journal and Snapshot store" in {
    EnvironmentHelper.setEnv(BootConfig.COS_JOURNAL_USE_LEGACY_SCHEMA, "false")
    val config: Config = ConfigFactory.parseResources("schemas-util.conf").resolve()
    noException shouldBe thrownBy(SchemasUtil.createIfNotExists(config))
  }
}
