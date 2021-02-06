/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.migration

import com.typesafe.config.{Config, ConfigFactory}
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.scalamock.scalatest.MockFactory
import org.scalatest._
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class SchemasUtilSpec
    extends AnyWordSpecLike
    with Matchers
    with TestSuite
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with OptionValues
    with TryValues
    with ScalaFutures
    with Eventually
    with MockFactory {
  var pg: EmbeddedPostgres = null
  override protected def beforeAll() = {
    val builder: EmbeddedPostgres.Builder = EmbeddedPostgres.builder()
    builder.setPort(25432)
    pg = builder.start()
  }

  override protected def afterAll() = {
    pg.close()
  }

  ".create chiefofstate stores" in {
    val config: Config = ConfigFactory.parseResources("schemas-util.conf").resolve()
    val schemasUtil = SchemasUtil(config)
    val result = schemasUtil.createIfNotExists()
    result should contain only ("0.7.0", "0.8.0")
  }
}
