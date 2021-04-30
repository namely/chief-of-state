/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.readside

import java.util.concurrent.FutureTask
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.Try

class Cancellable[T](executionContext: ExecutionContext, todo: => T) {
  private val promise: Promise[T] = Promise[T]()

  def future: Future[T] = promise.future

  private val jf: FutureTask[T] = new FutureTask[T](() => todo) {
    override def done(): Unit = promise.complete(Try(get()))
  }

  def cancel(): Unit = jf.cancel(true)

  executionContext.execute(jf)
}

object Cancellable {
  def apply[T](todo: => T)(implicit executionContext: ExecutionContext): Cancellable[T] =
    new Cancellable[T](executionContext, todo)
}
