/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.readside
import com.google.protobuf.any
import com.namely.chiefofstate.config.GrpcConfig
import com.namely.protobuf.chiefofstate.v1.common.MetaData
import com.namely.protobuf.chiefofstate.v1.readside.{ HandleReadSideStreamRequest, HandleReadSideStreamResponse }
import com.namely.protobuf.chiefofstate.v1.readside.ReadSideHandlerServiceGrpc.ReadSideHandlerServiceStub
import io.grpc.stub.StreamObserver
import io.grpc.Status
import org.slf4j.{ Logger, LoggerFactory }

import java.util.concurrent.{ CountDownLatch, TimeUnit }

/**
 *  Processes events read from the Journal by sending them to the read side server
 *  as gRPC stream
 */
private[readside] trait ReadSideStreamHandler {

  /**
   *  handles a sequence of events that will be used to build a read model
   *
   * @param events the sequence of events to handle
   */
  def processEvents(events: Seq[(any.Any, any.Any, MetaData)]): Unit
}

/**
 * Receives the readside response from an observable stream of messages.
 *
 * @param processorId the processor id
 * @param doneSignal the async signal notification
 */
private[readside] case class HandleReadSideResponseStreamObserver(processorId: String, doneSignal: CountDownLatch)
    extends StreamObserver[HandleReadSideStreamResponse] {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def onNext(response: HandleReadSideStreamResponse): Unit = {
    // onNext will be called only once after the server has finished processing the messages
    logger.info("received a server response...")
    if (!response.successful) {
      val errMsg: String =
        s"read side streaming message not handled, processor=$processorId"
      logger.warn(errMsg)
      throw new RuntimeException(errMsg)
    }
  }

  override def onError(t: Throwable): Unit = {
    val status = Status.fromThrowable(t)
    val errMsg: String =
      s"read side streaming returned failure, processor=$processorId, cause=$status"
    logger.warn(errMsg)
    doneSignal.countDown()
  }

  override def onCompleted(): Unit = {
    // the server is done sending us data
    // onCompleted will be called right after onNext()
    doneSignal.countDown()
  }
}

/**
 * read side processor that sends messages to a gRPC server that implements
 * the ReadSideHandler service
 *
 * @param processorId the unique Id for this read side
 * @param readSideHandlerServiceStub a non-blocking client for a ReadSideHandler
 */
private[readside] case class ReadSideStreamHandlerImpl(
    processorId: String,
    grpcConfig: GrpcConfig,
    readSideHandlerServiceStub: ReadSideHandlerServiceStub,
    doneSignal: CountDownLatch = new CountDownLatch(1))
    extends ReadSideStreamHandler {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  /**
   *  handles a sequence of events that will be used to build a read model
   *
   * @param events the read event envelopes to handle
   * @return true or false
   */
  override def processEvents(events: Seq[(any.Any, any.Any, MetaData)]): Unit = {
    // create an instance of the response stream observer
    val readSideResponseStreamObserver: HandleReadSideResponseStreamObserver =
      HandleReadSideResponseStreamObserver(processorId = processorId, doneSignal = doneSignal)

    // create the readSide request observer
    val readSideRequestObserver: StreamObserver[HandleReadSideStreamRequest] =
      readSideHandlerServiceStub
        .withDeadlineAfter(grpcConfig.client.timeout, TimeUnit.MILLISECONDS)
        .handleReadSideStream(readSideResponseStreamObserver)

    try {
      val it = events.iterator
      val proceed: Boolean = doneSignal.getCount == 0
      while (proceed && it.hasNext) {
        val (event, resultingState, meta) = it.next()
        val readSideRequest: HandleReadSideStreamRequest =
          HandleReadSideStreamRequest()
            .withEvent(event)
            .withState(resultingState)
            .withMeta(meta)
            .withReadSideId(processorId)

        // send the request to the server
        readSideRequestObserver.onNext(readSideRequest)
      }
    } catch {
      case e: RuntimeException =>
        logger.error(s"read side processing failure, processor=$processorId, cause=${e.getMessage}")
        // Cancel RPC call
        readSideRequestObserver.onError(e)
        throw e;
    }

    // we tell the server that the client is done sending data
    readSideRequestObserver.onCompleted()

    // Receiving happens asynchronously.
    doneSignal.await()
  }
}
