package com.namely.chiefofstate

import akka.actor.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.GrpcServiceException
import akka.grpc.scaladsl.{BytesEntry, Metadata, StringEntry}
import com.google.protobuf.ByteString
import com.google.protobuf.any.Any
import com.namely.chiefofstate.config.SendCommandSettings
import com.namely.chiefofstate.plugin.{ActivePlugins, PluginBase}
import com.namely.protobuf.chiefofstate.v1.internal.RemoteCommand
import com.namely.protobuf.chiefofstate.v1.service._
import io.grpc.Status
import io.superflat.lagompb.{AggregateRoot, BaseGrpcServiceImpl}
import io.superflat.lagompb.protobuf.v1.core.StateWrapper
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

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

  log.info("debug 2020.10.13")
  log.info(s"sendCommandSettings: $sendCommandSettings")
  log.info(s"sendCommandSettings.propagatedHeaders: ${sendCommandSettings.propagatedHeaders}")

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

      val meta: Map[String, Any] = plugins.foldLeft(Map[String, Any]())((metaMap, plugin) => {
        val pluginRun: Try[Map[String, Any]] = plugin.run(metadata)

        pluginRun match {
          case Success(m) => metaMap ++ m
          case Failure(e) =>
            //TODO Throw or return some sort of error
            Map[String, Any]()
        }
      })

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

      sendCommand(in.entityId, remoteCommand, meta)
        .map((stateWrapper: StateWrapper) => {
          ProcessCommandResponse(
            state = stateWrapper.state,
            meta = stateWrapper.meta.map(Util.toCosMetaData)
          )
        })
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
}
