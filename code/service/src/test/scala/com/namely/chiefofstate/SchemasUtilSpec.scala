/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

import com.namely.chiefofstate.helper.BaseSpec
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

  ".create Journal and Snapshot store" in {
    val config: Config = ConfigFactory.parseResources("schemas-util.conf").resolve()
    noException shouldBe thrownBy(SchemasUtil.createIfNotExists(config))
  }
}
