package com.namely.chiefofstate.grpc.client

import akka.event.LoggingAdapter
import akka.grpc.GrpcClientSettings
import akka.grpc.internal.{Marshaller, NettyClientUtils, ScalaUnaryRequestBuilder}
import akka.grpc.scaladsl.{AkkaGrpcClient, SingleResponseRequestBuilder}
import com.namely.protobuf.chiefofstate.v1.writeside.WriteSideHandlerService
import io.grpc.{CallOptions, MethodDescriptor}

import scala.concurrent.ExecutionContextExecutor

// Not sealed so users can extend to write their stubs
trait WriteSideHandlerServiceClient
    extends WriteSideHandlerService
    with WriteSideHandlerServiceClientPowerApi
    with AkkaGrpcClient

object WriteSideHandlerServiceClient {
  def apply(
    settings: GrpcClientSettings
  )(implicit ec: ExecutionContextExecutor, log: LoggingAdapter): WriteSideHandlerServiceClient =
    new DefaultWriteSideHandlerServiceClient(settings)
}

final class DefaultWriteSideHandlerServiceClient(settings: GrpcClientSettings)(implicit
  ec: ExecutionContextExecutor,
  log: LoggingAdapter
) extends WriteSideHandlerServiceClient {
  import DefaultWriteSideHandlerServiceClient._

  private val options: CallOptions = NettyClientUtils.callOptions(settings)
  private val clientState: GrpcClientState = new GrpcClientState(settings, log)

  private def handleCommandRequestBuilder(channel: akka.grpc.internal.InternalChannel) = {
    new ScalaUnaryRequestBuilder(handleCommandDescriptor, channel, options, settings)
  }

  private def handleEventRequestBuilder(channel: akka.grpc.internal.InternalChannel) = {
    new ScalaUnaryRequestBuilder(handleEventDescriptor, channel, options, settings)
  }

  /**
   * Lower level "lifted" version of the method, giving access to request metadata etc.
   * prefer handleCommand(com.namely.protobuf.chiefofstate.v1.writeside.HandleCommandRequest) if possible.
   */
  override def handleCommand()
    : SingleResponseRequestBuilder[com.namely.protobuf.chiefofstate.v1.writeside.HandleCommandRequest,
                                   com.namely.protobuf.chiefofstate.v1.writeside.HandleCommandResponse
    ] =
    handleCommandRequestBuilder(clientState.internalChannel)

  /**
   * For access to method metadata use the parameterless version of handleCommand
   */
  def handleCommand(
    in: com.namely.protobuf.chiefofstate.v1.writeside.HandleCommandRequest
  ): scala.concurrent.Future[com.namely.protobuf.chiefofstate.v1.writeside.HandleCommandResponse] =
    handleCommand().invoke(in)

  /**
   * Lower level "lifted" version of the method, giving access to request metadata etc.
   * prefer handleEvent(com.namely.protobuf.chiefofstate.v1.writeside.HandleEventRequest) if possible.
   */

  override def handleEvent()
    : SingleResponseRequestBuilder[com.namely.protobuf.chiefofstate.v1.writeside.HandleEventRequest,
                                   com.namely.protobuf.chiefofstate.v1.writeside.HandleEventResponse
    ] =
    handleEventRequestBuilder(clientState.internalChannel)

  /**
   * For access to method metadata use the parameterless version of handleEvent
   */
  def handleEvent(
    in: com.namely.protobuf.chiefofstate.v1.writeside.HandleEventRequest
  ): scala.concurrent.Future[com.namely.protobuf.chiefofstate.v1.writeside.HandleEventResponse] =
    handleEvent().invoke(in)

  override def close(): scala.concurrent.Future[akka.Done] = clientState.close()
  override def closed: scala.concurrent.Future[akka.Done] = clientState.closed()
}

private object DefaultWriteSideHandlerServiceClient {

  def apply(
    settings: GrpcClientSettings
  )(implicit ec: ExecutionContextExecutor, log: LoggingAdapter): WriteSideHandlerServiceClient =
    new DefaultWriteSideHandlerServiceClient(settings)

  import WriteSideHandlerService.Serializers._

  private val handleCommandDescriptor
    : MethodDescriptor[com.namely.protobuf.chiefofstate.v1.writeside.HandleCommandRequest,
                       com.namely.protobuf.chiefofstate.v1.writeside.HandleCommandResponse
    ] =
    MethodDescriptor
      .newBuilder()
      .setType(
        MethodDescriptor.MethodType.UNARY
      )
      .setFullMethodName(
        MethodDescriptor.generateFullMethodName("chief_of_state.v1.WriteSideHandlerService", "HandleCommand")
      )
      .setRequestMarshaller(new Marshaller(HandleCommandRequestSerializer))
      .setResponseMarshaller(new Marshaller(HandleCommandResponseSerializer))
      .setSampledToLocalTracing(true)
      .build()

  private val handleEventDescriptor: MethodDescriptor[com.namely.protobuf.chiefofstate.v1.writeside.HandleEventRequest,
                                                      com.namely.protobuf.chiefofstate.v1.writeside.HandleEventResponse
  ] =
    MethodDescriptor
      .newBuilder()
      .setType(
        MethodDescriptor.MethodType.UNARY
      )
      .setFullMethodName(
        MethodDescriptor.generateFullMethodName("chief_of_state.v1.WriteSideHandlerService", "HandleEvent")
      )
      .setRequestMarshaller(new Marshaller(HandleEventRequestSerializer))
      .setResponseMarshaller(new Marshaller(HandleEventResponseSerializer))
      .setSampledToLocalTracing(true)
      .build()
}

trait WriteSideHandlerServiceClientPowerApi {

  /**
   * Lower level "lifted" version of the method, giving access to request metadata etc.
   * prefer handleCommand(com.namely.protobuf.chiefofstate.v1.writeside.HandleCommandRequest) if possible.
   */
  def handleCommand(): SingleResponseRequestBuilder[com.namely.protobuf.chiefofstate.v1.writeside.HandleCommandRequest,
                                                    com.namely.protobuf.chiefofstate.v1.writeside.HandleCommandResponse
  ] = ???

  /**
   * Lower level "lifted" version of the method, giving access to request metadata etc.
   * prefer handleEvent(com.namely.protobuf.chiefofstate.v1.writeside.HandleEventRequest) if possible.
   */
  def handleEvent(): SingleResponseRequestBuilder[com.namely.protobuf.chiefofstate.v1.writeside.HandleEventRequest,
                                                  com.namely.protobuf.chiefofstate.v1.writeside.HandleEventResponse
  ] = ???
}
