/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.telemetry

import io.opentelemetry.context.Context

import java.util
import java.util.concurrent.{Callable, ExecutorService, ForkJoinPool, Future, TimeUnit}
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._

/**
 * A custom Executor service that wraps all tasks it receives with the current telemetry context
 *  before handing the tasks off to underlying delegate executor.
 * @param delegate an executor service that will actually handle running the tasks.
 */
class TracedExecutorService(delegate: ExecutorService) extends ExecutorService {
  override def shutdown(): Unit = delegate.shutdown()

  override def shutdownNow(): util.List[Runnable] = delegate.shutdownNow()

  override def isShutdown: Boolean = delegate.isShutdown

  override def isTerminated: Boolean = delegate.isTerminated

  override def awaitTermination(timeout: Long, unit: TimeUnit): Boolean = delegate.awaitTermination(timeout, unit)

  override def submit[T](task: Callable[T]): Future[T] = {
    val wrappedTask = Context.current().wrap(task)
    delegate.submit(wrappedTask)
  }

  override def submit[T](task: Runnable, result: T): Future[T] = {
    val wrappedTask = Context.current().wrap(task)
    delegate.submit(wrappedTask, result)
  }

  override def submit(task: Runnable): Future[_] = {
    val wrappedTask = Context.current().wrap(task)
    delegate.submit(wrappedTask)
  }

  override def invokeAll[T](tasks: util.Collection[_ <: Callable[T]]): util.List[Future[T]] = {
    val wrappedTask: Seq[Callable[T]] = tasks.asScala.map(t => Context.current().wrap(t)).toSeq
    delegate.invokeAll(wrappedTask.asJava)
  }

  override def invokeAll[T](tasks: util.Collection[_ <: Callable[T]],
                            timeout: Long,
                            unit: TimeUnit
  ): util.List[Future[T]] = {
    val wrappedTask: Seq[Callable[T]] = tasks.asScala.map(t => Context.current().wrap(t)).toSeq
    delegate.invokeAll(wrappedTask.asJava, timeout, unit)
  }

  override def invokeAny[T](tasks: util.Collection[_ <: Callable[T]]): T = {
    val wrappedTask: Seq[Callable[T]] = tasks.asScala.map(t => Context.current().wrap(t)).toSeq
    delegate.invokeAny(wrappedTask.asJava)
  }

  override def invokeAny[T](tasks: util.Collection[_ <: Callable[T]], timeout: Long, unit: TimeUnit): T = {
    val wrappedTask: Seq[Callable[T]] = tasks.asScala.map(t => Context.current().wrap(t)).toSeq
    delegate.invokeAny(wrappedTask.asJava, timeout, unit)
  }

  override def execute(command: Runnable): Unit = {
    val wrappedCommand = Context.current().wrap(command)
    delegate.execute(wrappedCommand)
  }
}

object TracedExecutorService {

  /**
   * Returns an Execution Context created from a ForkJoinPool wrapped in a TracedExecutorService
   * to ensure that tasks executed in the ForkJoinPool propagate the current telemetry Context.
   */
  def get(): ExecutionContext = {
    // compute parallelism as 10x the CPU cores
    // TODO: maybe raise this
    val threadMultiplier = 10
    val parallelism = Runtime.getRuntime.availableProcessors * threadMultiplier
    // create fork join pool
    val pool: ForkJoinPool = new ForkJoinPool(
      parallelism,
      ForkJoinPool.defaultForkJoinWorkerThreadFactory,
      null,
      true
    )
    val tracedEc: ExecutorService = new TracedExecutorService(pool)
    ExecutionContext.fromExecutorService(tracedEc)
  }
}
