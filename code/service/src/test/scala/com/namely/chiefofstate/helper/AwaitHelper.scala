/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.helper

import java.util.concurrent.Executors
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future }

/**
 * test helper to await some condition
 */
object AwaitHelper {
  def await(exitCondition: () => Boolean, duration: Duration): Unit = {
    val e = Executors.newFixedThreadPool(1)
    implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(e)

    def future =
      Future {
        while (!exitCondition()) Thread.sleep(2)
      }

    Await.ready(future, duration)
  }
}
