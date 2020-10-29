package com.namely.chiefofstate


import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import io.grpc.Status

import akka.actor.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.GrpcServiceException
import akka.grpc.scaladsl.{BytesEntry, Metadata, StringEntry}

import com.google.protobuf.ByteString
import com.google.protobuf.any.Any
import com.google.rpc.status.{Status => RpcStatus}

import org.slf4j.{Logger, LoggerFactory}

import com.namely.chiefofstate.config.SendCommandSettings
import com.namely.chiefofstate.plugin.PluginBase
import com.namely.chiefofstate.plugin.PluginRunner.PluginBaseImplicits
import com.namely.chiefofstate.plugin.utils.MetadataUtil
import com.namely.protobuf.chiefofstate.v1.internal.RemoteCommand
import com.namely.protobuf.chiefofstate.v1.service._

import io.superflat.lagompb.{AggregateRoot, BaseGrpcServiceImpl}
import io.superflat.lagompb.protobuf.v1.core.StateWrapper
import io.superflat.lagompb.protobuf.v1.core.FailureResponse
import io.superflat.lagompb.protobuf.v1.core.FailureResponse.FailureType.Custom

class GrpcServiceImpl(sys: ActorSystem,
                      val clusterSharding: ClusterSharding,
                      val aggregateRoot: AggregateRoot,
                      val sendCommandSettings: SendCommandSettings,
                      plugins: Seq[PluginBase] = Seq()
)(implicit
  ec: ExecutionContext
) extends AbstractChiefOfStateServicePowerApiRouter(sys)
    with BaseGrpcServiceImpl {

  private val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * gRPC ProcessCommand implementation
   *
   * @param in the ProcessCommandRequest
   * @param metadata akka gRPC metadata
   * @return future with the command response
   */
  override def processCommand(in: ProcessCommandRequest, metadata: Metadata): Future[ProcessCommandResponse] = {
    if (in.entityId.isEmpty) {
      log.error(s"request missing entity id")
      Future.fromTry(
        Failure(new GrpcServiceException(status = Status.INVALID_ARGUMENT.withDescription("empty entity ID")))
      )
    } else {

      val meta: Try[Map[String, Any]] = plugins.foldLeft(Try(Map[String, Any]()))((metaMap, plugin) => {
        val pluginRun: Try[Map[String, Any]] = plugin.run(in, MetadataUtil.makeMeta(metadata))

        pluginRun match {
          case Success(m) => Try(metaMap.get ++ m)
          case Failure(e) => throw new GrpcServiceException(status = Status.ABORTED.withDescription(e.getMessage))
        }
      })

      meta match {
        case Success(m) =>
          // get the headers to forward
          val propagatedHeaders: Seq[RemoteCommand.Header] = metadata.asList
            // filter to relevant headers
            .filter({ case (k, _) => sendCommandSettings.propagatedHeaders.contains(k) })
            .map({
              case (k, StringEntry(value)) =>
                RemoteCommand
                  .Header()
                  .withKey(k)
                  .withStringValue(value)

              case (k, BytesEntry(value)) =>
                RemoteCommand
                  .Header()
                  .withKey(k)
                  .withBytesValue(ByteString.copyFrom(value.toArray))
            })

          val remoteCommand: RemoteCommand = RemoteCommand()
            .withCommand(in.getCommand)
            .withHeaders(propagatedHeaders)

          sendCommand(in.entityId, remoteCommand, m)
            .map((stateWrapper: StateWrapper) => {
              ProcessCommandResponse(
                state = stateWrapper.state,
                meta = stateWrapper.meta.map(Util.toCosMetaData)
              )
            })
        case Failure(e) => Future.fromTry(Failure(e))
      }
    }
  }

  /**
   * gRPC GetState implementation
   *
   * @param in GetStateRequest
   * @param metadata akka gRPC metadata
   * @return future of GetStateResponse
   */
  override def getState(in: GetStateRequest, metadata: Metadata): Future[GetStateResponse] = {
    if (in.entityId.isEmpty) {
      log.error(s"request missing entity id")
      Future.fromTry(
        Failure(new GrpcServiceException(status = Status.INVALID_ARGUMENT.withDescription("empty entity ID")))
      )
    } else {
      sendCommand(in.entityId, in, Map.empty[String, Any])
        .map((stateWrapper: StateWrapper) => {
          GetStateResponse(
            state = stateWrapper.state,
            meta = stateWrapper.meta.map(Util.toCosMetaData)
          )
        })
    }
  }

  /**
   * override lagom-pb custom error handling
   *
   * @param failureResponse a lagom-pb failure response from send command
   * @return a failure
   */
  override def transformFailedReply(failureResponse: FailureResponse): Failure[Throwable] = {
    val statusTypeUrl = RpcStatus.scalaDescriptor.fullName.split("/").last

    failureResponse.failureType match {
      case Custom(value) if value.typeUrl.split("/").last == statusTypeUrl =>
        val rpcStatus = value.unpack(RpcStatus)

        val status = Status
          .fromCodeValue(rpcStatus.code)
          .withDescription(rpcStatus.message)

        Failure(new GrpcServiceException(status = status))

      case _ =>
        super.transformFailedReply(failureResponse)
    }
  }
}
