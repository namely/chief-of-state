package com.namely.chiefofstate.telemetry

import io.opentracing.contrib.concurrent.TracedExecutorService
import io.opentracing.Tracer
import io.opentracing.util.GlobalTracer

import java.util.concurrent.{ExecutorService, ForkJoinPool}
import scala.concurrent.ExecutionContext

/**
 * Creates a traced fork join pool for running the application
 */
object TracedExecutionContext {
  def get(tracer: Tracer): ExecutionContext = {
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
    // build the traced executor service
    val tracedEc: ExecutorService = new TracedExecutorService(pool, tracer, true)
    ExecutionContext.fromExecutorService(tracedEc)
  }

  def get(): ExecutionContext = get(GlobalTracer.get())
}
