/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.readside

import akka.actor.typed.ActorSystem
import com.namely.chiefofstate.NettyHelper
import com.namely.chiefofstate.config.{ GrpcConfig, ReadSideConfig, ReadSideConfigReader }
import com.namely.chiefofstate.readside.streaming.{ ReadSideStreamHandlerImpl, ReadSideStreamProjection }
import com.namely.protobuf.chiefofstate.v1.readside.ReadSideHandlerServiceGrpc.{
  ReadSideHandlerServiceBlockingStub,
  ReadSideHandlerServiceStub
}
import com.typesafe.config.Config
import com.zaxxer.hikari.{ HikariConfig, HikariDataSource }
import io.grpc.ClientInterceptor
import org.slf4j.{ Logger, LoggerFactory }

/**
 * Used to configure and start all read side processors
 *
 * @param system actor system
 * @param interceptors sequence of interceptors for the gRPC client
 * @param dbConfig the DB config for creating a hikari data source
 * @param readSideConfigs sequence of configs for specific read sides
 * @param numShards number of shards for projections/tags
 * @param grpcConfig the grpc configuration
 */
class ReadSideManager(
    system: ActorSystem[_],
    interceptors: Seq[ClientInterceptor],
    dbConfig: ReadSideManager.DbConfig,
    readSideConfigs: Seq[ReadSideConfig],
    numShards: Int,
    grpcConfig: GrpcConfig) {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  private[readside] lazy val dataSource: HikariDataSource =
    ReadSideManager.getDataSource(dbConfig)

  def init(): Unit = {

    logger.info(s"initializing read sides, count=${readSideConfigs.size}")

    // configure each read side
    readSideConfigs.foreach(rsconfig => {

      logger.info(s"starting read side, id=${rsconfig.processorId}")
      // FIXME draft implementation
      if (rsconfig.useStreaming) {
        // construct a remote gRPC streaming read side client
        val rpcStreamingClient =
          new ReadSideHandlerServiceStub(NettyHelper.builder(rsconfig.host, rsconfig.port, rsconfig.useTls).build)
            .withInterceptors(interceptors: _*)

        // instantiate a remote read side stream processor with the gRPC client
        val remoteReadSideStreamProcessor: ReadSideStreamHandlerImpl =
          ReadSideStreamHandlerImpl(rsconfig.processorId, grpcConfig, rpcStreamingClient)

        // instantiate the read side projection with the remote processor
        val projection: ReadSideStreamProjection =
          new ReadSideStreamProjection(
            system,
            rsconfig.processorId,
            dataSource,
            remoteReadSideStreamProcessor,
            numShards)
        // start the sharded daemon process
        projection.start()
      } else {
        // construct a remote gRPC read side client for this read side
        // and register interceptors
        val rpcClient: ReadSideHandlerServiceBlockingStub = new ReadSideHandlerServiceBlockingStub(
          NettyHelper.builder(rsconfig.host, rsconfig.port, rsconfig.useTls).build).withInterceptors(interceptors: _*)
        // instantiate a remote read side processor with the gRPC client
        val remoteReadSideProcessor: ReadSideHandlerImpl = new ReadSideHandlerImpl(rsconfig.processorId, rpcClient)
        // instantiate the read side projection with the remote processor
        val projection =
          new ReadSideProjection(system, rsconfig.processorId, dataSource, remoteReadSideProcessor, numShards)
        // start the sharded daemon process
        projection.start()
      }
    })
  }
}

object ReadSideManager {

  def apply(system: ActorSystem[_], interceptors: Seq[ClientInterceptor], numShards: Int): ReadSideManager = {

    val dbConfig: DbConfig = {
      // read the jdbc-default settings
      val jdbcCfg: Config = system.settings.config.getConfig("jdbc-default")

      DbConfig(jdbcCfg)
    }

    val grpcConfig: GrpcConfig = GrpcConfig(system.settings.config)

    // get the individual read side configs
    val configs: Seq[ReadSideConfig] = ReadSideConfigReader.getReadSideSettings
    // make the manager
    new ReadSideManager(
      system = system,
      interceptors = interceptors,
      dbConfig = dbConfig,
      readSideConfigs = configs,
      numShards = numShards,
      grpcConfig)
  }

  /**
   * create a hikari data source using a dbconfig class
   *
   * @param dbConfig database configs
   * @return a hikari data source instance
   */
  def getDataSource(dbConfig: DbConfig): HikariDataSource = {
    // make a hikari config for this db
    val hikariCfg: HikariConfig = new HikariConfig()
    // apply settings
    hikariCfg.setPoolName("cos-readside-pool")
    // FIXME, apply schema here as a hikari setting?
    hikariCfg.setJdbcUrl(dbConfig.jdbcUrl)
    hikariCfg.setUsername(dbConfig.username)
    hikariCfg.setPassword(dbConfig.password)
    // turn off autocommit so that akka can control it
    hikariCfg.setAutoCommit(false)
    // set max pool size
    hikariCfg.setMaximumPoolSize(dbConfig.maxPoolSize)
    // set min pool size
    hikariCfg.setMinimumIdle(dbConfig.minIdleConnections)
    // set pool idle timeout
    hikariCfg.setIdleTimeout(dbConfig.idleTimeoutMs)
    // connection lifetime after close
    hikariCfg.setMaxLifetime(dbConfig.maxLifetimeMs)
    // return the data source
    new HikariDataSource(hikariCfg)
  }

  // convenience case class for passing around the hikari settings
  private[readside] case class DbConfig(
      jdbcUrl: String,
      username: String,
      password: String,
      maxPoolSize: Int,
      minIdleConnections: Int,
      idleTimeoutMs: Long,
      maxLifetimeMs: Long)

  private[readside] object DbConfig {
    def apply(jdbcCfg: Config): DbConfig = {
      DbConfig(
        jdbcUrl = jdbcCfg.getString("url"),
        username = jdbcCfg.getString("user"),
        password = jdbcCfg.getString("password"),
        maxPoolSize = jdbcCfg.getInt("hikari-settings.max-pool-size"),
        minIdleConnections = jdbcCfg.getInt("hikari-settings.min-idle-connections"),
        idleTimeoutMs = jdbcCfg.getLong("hikari-settings.idle-timeout-ms"),
        maxLifetimeMs = jdbcCfg.getLong("hikari-settings.max-lifetime-ms"))
    }
  }
}
