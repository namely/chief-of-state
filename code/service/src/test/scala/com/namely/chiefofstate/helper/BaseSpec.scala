/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.helper

import org.scalamock.scalatest.MockFactory
import org.scalatest._
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{ Seconds, Span }
import org.scalatest.wordspec.AnyWordSpecLike

trait BaseSpec
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

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(30, Seconds), interval = Span(100, org.scalatest.time.Millis))

  // define set of resources to close after each test
  val closeables: GrpcHelpers.Closeables = new GrpcHelpers.Closeables()

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    closeables.closeAll()
  }

  override protected def afterEach(): Unit = {
    super.afterEach()
    closeables.closeAll()
  }
}
