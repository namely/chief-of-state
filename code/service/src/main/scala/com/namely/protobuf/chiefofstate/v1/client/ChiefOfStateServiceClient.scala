package com.namely.protobuf.chiefofstate.v1.client

import akka.event.LoggingAdapter
import akka.grpc.GrpcClientSettings
import akka.grpc.internal.{Marshaller, NettyClientUtils, ScalaUnaryRequestBuilder}
import akka.grpc.scaladsl.{AkkaGrpcClient, SingleResponseRequestBuilder}
import com.namely.protobuf.chiefofstate.v1.service.ChiefOfStateService
import io.grpc.{CallOptions, MethodDescriptor}

import scala.concurrent.ExecutionContextExecutor

// Not sealed so users can extend to write their stubs
trait ChiefOfStateServiceClient extends ChiefOfStateService with ChiefOfStateServiceClientPowerApi with AkkaGrpcClient

object ChiefOfStateServiceClient {
  def apply(
    settings: GrpcClientSettings
  )(implicit ec: ExecutionContextExecutor, log: LoggingAdapter): ChiefOfStateServiceClient =
    new DefaultChiefOfStateServiceClient(settings)
}

final class DefaultChiefOfStateServiceClient(settings: GrpcClientSettings)(implicit
  ec: ExecutionContextExecutor,
  log: LoggingAdapter
) extends ChiefOfStateServiceClient {

  import DefaultChiefOfStateServiceClient._

  private val options: CallOptions = NettyClientUtils.callOptions(settings)
  private val clientState: GrpcClientState = new GrpcClientState(settings, log)

  private def processCommandRequestBuilder(channel: akka.grpc.internal.InternalChannel) = {

    new ScalaUnaryRequestBuilder(processCommandDescriptor, channel, options, settings)
  }

  private def getStateRequestBuilder(channel: akka.grpc.internal.InternalChannel) = {

    new ScalaUnaryRequestBuilder(getStateDescriptor, channel, options, settings)
  }

  /**
   * Lower level "lifted" version of the method, giving access to request metadata etc.
   * prefer processCommand(com.namely.protobuf.chiefofstate.v1.service.ProcessCommandRequest) if possible.
   */

  override def processCommand()
    : SingleResponseRequestBuilder[com.namely.protobuf.chiefofstate.v1.service.ProcessCommandRequest,
                                   com.namely.protobuf.chiefofstate.v1.service.ProcessCommandResponse
    ] =
    processCommandRequestBuilder(clientState.internalChannel)

  /**
   * For access to method metadata use the parameterless version of processCommand
   */
  def processCommand(
    in: com.namely.protobuf.chiefofstate.v1.service.ProcessCommandRequest
  ): scala.concurrent.Future[com.namely.protobuf.chiefofstate.v1.service.ProcessCommandResponse] =
    processCommand().invoke(in)

  /**
   * Lower level "lifted" version of the method, giving access to request metadata etc.
   * prefer getState(com.namely.protobuf.chiefofstate.v1.service.GetStateRequest) if possible.
   */

  override def getState(): SingleResponseRequestBuilder[com.namely.protobuf.chiefofstate.v1.service.GetStateRequest,
                                                        com.namely.protobuf.chiefofstate.v1.service.GetStateResponse
  ] =
    getStateRequestBuilder(clientState.internalChannel)

  /**
   * For access to method metadata use the parameterless version of getState
   */
  def getState(
    in: com.namely.protobuf.chiefofstate.v1.service.GetStateRequest
  ): scala.concurrent.Future[com.namely.protobuf.chiefofstate.v1.service.GetStateResponse] =
    getState().invoke(in)

  override def close(): scala.concurrent.Future[akka.Done] = clientState.close()

  override def closed: scala.concurrent.Future[akka.Done] = clientState.closed()
}

private object DefaultChiefOfStateServiceClient {

  def apply(
    settings: GrpcClientSettings
  )(implicit ec: ExecutionContextExecutor, log: LoggingAdapter): ChiefOfStateServiceClient =
    new DefaultChiefOfStateServiceClient(settings)

  import com.namely.protobuf.chiefofstate.v1.service.ChiefOfStateService.Serializers._

  private val processCommandDescriptor
    : MethodDescriptor[com.namely.protobuf.chiefofstate.v1.service.ProcessCommandRequest,
                       com.namely.protobuf.chiefofstate.v1.service.ProcessCommandResponse
    ] =
    MethodDescriptor
      .newBuilder()
      .setType(
        MethodDescriptor.MethodType.UNARY
      )
      .setFullMethodName(
        MethodDescriptor.generateFullMethodName("chief_of_state.v1.ChiefOfStateService", "ProcessCommand")
      )
      .setRequestMarshaller(new Marshaller(ProcessCommandRequestSerializer))
      .setResponseMarshaller(new Marshaller(ProcessCommandResponseSerializer))
      .setSampledToLocalTracing(true)
      .build()

  private val getStateDescriptor: MethodDescriptor[com.namely.protobuf.chiefofstate.v1.service.GetStateRequest,
                                                   com.namely.protobuf.chiefofstate.v1.service.GetStateResponse
  ] =
    MethodDescriptor
      .newBuilder()
      .setType(
        MethodDescriptor.MethodType.UNARY
      )
      .setFullMethodName(MethodDescriptor.generateFullMethodName("chief_of_state.v1.ChiefOfStateService", "GetState"))
      .setRequestMarshaller(new Marshaller(GetStateRequestSerializer))
      .setResponseMarshaller(new Marshaller(GetStateResponseSerializer))
      .setSampledToLocalTracing(true)
      .build()

}

trait ChiefOfStateServiceClientPowerApi {

  /**
   * Lower level "lifted" version of the method, giving access to request metadata etc.
   * prefer processCommand(com.namely.protobuf.chiefofstate.v1.service.ProcessCommandRequest) if possible.
   */
  def processCommand(): SingleResponseRequestBuilder[com.namely.protobuf.chiefofstate.v1.service.ProcessCommandRequest,
                                                     com.namely.protobuf.chiefofstate.v1.service.ProcessCommandResponse
  ] = ???

  /**
   * Lower level "lifted" version of the method, giving access to request metadata etc.
   * prefer getState(com.namely.protobuf.chiefofstate.v1.service.GetStateRequest) if possible.
   */
  def getState(): SingleResponseRequestBuilder[com.namely.protobuf.chiefofstate.v1.service.GetStateRequest,
                                               com.namely.protobuf.chiefofstate.v1.service.GetStateResponse
  ] = ???
}
