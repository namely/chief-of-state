package com.namely.chiefofstate.grpc.client

import akka.event.LoggingAdapter
import akka.grpc.GrpcClientSettings
import akka.grpc.internal.{Marshaller, NettyClientUtils, ScalaUnaryRequestBuilder}
import akka.grpc.scaladsl.{AkkaGrpcClient, SingleResponseRequestBuilder}
import com.namely.protobuf.chiefofstate.v1.readside.ReadSideHandlerService
import io.grpc.{CallOptions, MethodDescriptor}

import scala.concurrent.ExecutionContextExecutor

// Not sealed so users can extend to write their stubs
trait ReadSideHandlerServiceClient
    extends ReadSideHandlerService
    with ReadSideHandlerServiceClientPowerApi
    with AkkaGrpcClient

object ReadSideHandlerServiceClient {
  def apply(
    settings: GrpcClientSettings
  )(implicit ec: ExecutionContextExecutor, log: LoggingAdapter): ReadSideHandlerServiceClient =
    new DefaultReadSideHandlerServiceClient(settings)
}

final class DefaultReadSideHandlerServiceClient(settings: GrpcClientSettings)(implicit
  ec: ExecutionContextExecutor,
  log: LoggingAdapter
) extends ReadSideHandlerServiceClient {

  import DefaultReadSideHandlerServiceClient._

  private val options: CallOptions = NettyClientUtils.callOptions(settings)
  private val clientState: GrpcClientState = new GrpcClientState(settings, log)

  private def handleReadSideRequestBuilder(channel: akka.grpc.internal.InternalChannel) = {

    new ScalaUnaryRequestBuilder(handleReadSideDescriptor, channel, options, settings)

  }

  /**
   * Lower level "lifted" version of the method, giving access to request metadata etc.
   * prefer handleReadSide(com.namely.protobuf.chiefofstate.v1.readside.HandleReadSideRequest) if possible.
   */

  override def handleReadSide()
    : SingleResponseRequestBuilder[com.namely.protobuf.chiefofstate.v1.readside.HandleReadSideRequest,
                                   com.namely.protobuf.chiefofstate.v1.readside.HandleReadSideResponse
    ] =
    handleReadSideRequestBuilder(clientState.internalChannel)

  /**
   * For access to method metadata use the parameterless version of handleReadSide
   */
  def handleReadSide(
    in: com.namely.protobuf.chiefofstate.v1.readside.HandleReadSideRequest
  ): scala.concurrent.Future[com.namely.protobuf.chiefofstate.v1.readside.HandleReadSideResponse] =
    handleReadSide().invoke(in)

  override def close(): scala.concurrent.Future[akka.Done] = clientState.close()

  override def closed: scala.concurrent.Future[akka.Done] = clientState.closed()

}

private object DefaultReadSideHandlerServiceClient {

  def apply(
    settings: GrpcClientSettings
  )(implicit ec: ExecutionContextExecutor, log: LoggingAdapter): ReadSideHandlerServiceClient =
    new DefaultReadSideHandlerServiceClient(settings)

  import com.namely.protobuf.chiefofstate.v1.readside.ReadSideHandlerService.Serializers._

  private val handleReadSideDescriptor
    : MethodDescriptor[com.namely.protobuf.chiefofstate.v1.readside.HandleReadSideRequest,
                       com.namely.protobuf.chiefofstate.v1.readside.HandleReadSideResponse
    ] =
    MethodDescriptor
      .newBuilder()
      .setType(
        MethodDescriptor.MethodType.UNARY
      )
      .setFullMethodName(
        MethodDescriptor.generateFullMethodName("chief_of_state.v1.ReadSideHandlerService", "HandleReadSide")
      )
      .setRequestMarshaller(new Marshaller(HandleReadSideRequestSerializer))
      .setResponseMarshaller(new Marshaller(HandleReadSideResponseSerializer))
      .setSampledToLocalTracing(true)
      .build()

}

trait ReadSideHandlerServiceClientPowerApi {

  /**
   * Lower level "lifted" version of the method, giving access to request metadata etc.
   * prefer handleReadSide(com.namely.protobuf.chiefofstate.v1.readside.HandleReadSideRequest) if possible.
   */

  def handleReadSide(): SingleResponseRequestBuilder[com.namely.protobuf.chiefofstate.v1.readside.HandleReadSideRequest,
                                                     com.namely.protobuf.chiefofstate.v1.readside.HandleReadSideResponse
  ] = ???
}
