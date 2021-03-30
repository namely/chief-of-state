/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.persistence.typed.PersistenceId
import com.google.protobuf.any
import com.google.protobuf.any.Any
import com.google.protobuf.empty.Empty
import com.namely.chiefofstate.config.{CosConfig, EventsConfig}
import com.namely.chiefofstate.helper.BaseActorSpec
import com.namely.chiefofstate.AggregateRoot.getEntityId
import com.namely.protobuf.chiefofstate.v1.common.MetaData
import com.namely.protobuf.chiefofstate.v1.internal._
import com.namely.protobuf.chiefofstate.v1.internal.CommandReply.Reply
import com.namely.protobuf.chiefofstate.v1.internal.SendCommand.Type
import com.namely.protobuf.chiefofstate.v1.persistence.StateWrapper
import com.namely.protobuf.chiefofstate.v1.tests.{Account, AccountOpened, OpenAccount}
import com.namely.protobuf.chiefofstate.v1.writeside._
import com.namely.protobuf.chiefofstate.v1.writeside.WriteSideHandlerServiceGrpc.WriteSideHandlerServiceBlockingStub
import com.typesafe.config.{Config, ConfigFactory}
import io.grpc.{ManagedChannel, ServerServiceDefinition, Status}
import io.grpc.inprocess._

import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Future
import scala.util.{Failure, Try}

class AggregrateRootSpec extends BaseActorSpec(s"""
      akka.cluster.sharding.number-of-shards = 1
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "tmp/snapshot"
    """) {

  var cosConfig: CosConfig = _
  val actorSystem: ActorSystem[Nothing] = testKit.system
  val replyTimeout: FiniteDuration = FiniteDuration(30, TimeUnit.SECONDS)

  // register a server that intercepts traces and reports errors
  def createServer(serverName: String, service: ServerServiceDefinition): Unit = {
    closeables.register(
      InProcessServerBuilder
        .forName(serverName)
        .directExecutor()
        .addService(service)
        .build()
        .start()
    )
  }

  def getChannel(serverName: String): ManagedChannel = {
    closeables.register(
      InProcessChannelBuilder
        .forName(serverName)
        .directExecutor()
        .build()
    )
  }

  override def beforeAll(): Unit = {

    val config: Config = ConfigFactory.parseString(s"""
            akka.cluster.sharding.number-of-shards = 1
            chiefofstate {
             	service-name = "chiefofstate"
              ask-timeout = 5
              snapshot-criteria {
                disable-snapshot = false
                retention-frequency = 1
                retention-number = 1
                delete-events-on-snapshot = false
              }
              events {
                tagname: "cos"
              }
              grpc {
                client {
                  deadline-timeout = 3000
                }
                server {
                  address = "0.0.0.0"
                  port = 9000
                }
              }
              write-side {
                host = "localhost"
                port = 6000
                use-tls = false
                enable-protos-validation = false
                states-protos = ""
                events-protos = ""
                propagated-headers = ""
              }
              read-side {
                # set this value to true whenever a readSide config is set
                enabled = false
              }
              telemetry {
                namespace = ""
                otlp_endpoint = ""
                trace_propagators = "b3multi"
              }
            }
          """)
    cosConfig = CosConfig(config)
  }

  override protected def beforeEach(): Unit = {
    closeables.closeAll()
    super.beforeEach()
  }

  ".initialState" should {
    "return the aggregate initial state" in {
      val persistenceId: PersistenceId = PersistenceId("chiefofstate", "123")
      val initialState: StateWrapper = AggregateRoot.initialState(persistenceId)
      initialState.getMeta.entityId shouldBe "123"
      initialState.getMeta.revisionNumber shouldBe 0
    }
  }

  ".handleCommand" should {
    "return as expected" in {
      // define the ID's
      val aggregateId: String = UUID.randomUUID().toString
      val persistenceId: PersistenceId = PersistenceId("chiefofstate", aggregateId)

      // define prior state, command, and prior event meta
      val priorState: Any = Any.pack(Empty.defaultInstance)
      val command: Any = Any.pack(OpenAccount())
      val priorMeta: MetaData = MetaData.defaultInstance
        .withRevisionNumber(0)
        .withEntityId(aggregateId)

      // define event to return and handle command response
      val event: AccountOpened = AccountOpened()

      val handleCommandRequest = HandleCommandRequest()
        .withCommand(command)
        .withPriorState(priorState)
        .withPriorEventMeta(priorMeta)

      val handleCommandResponse = HandleCommandResponse()
        .withEvent(Any.pack(event))

      // define a resulting state
      val resultingState = Any.pack(Account().withAccountUuid(aggregateId).withBalance(200))

      // mock the write handler
      val serviceImpl = mock[WriteSideHandlerServiceGrpc.WriteSideHandlerService]

      (serviceImpl.handleCommand _)
        .expects(handleCommandRequest)
        .returning {
          Future.successful(handleCommandResponse)
        }

      (serviceImpl.handleEvent _)
        .expects(*)
        .onCall((request: HandleEventRequest) => {
          val output = Try {
            require(request.getEventMeta.revisionNumber == priorMeta.revisionNumber + 1)
            HandleEventResponse().withResultingState(resultingState)
          }
            .recoverWith({ case e: Throwable =>
              Failure(Util.makeStatusException(e))
            })
          Future.fromTry(output)
        })

      val service = WriteSideHandlerServiceGrpc.bindService(serviceImpl, global)
      val serverName = InProcessServerBuilder.generateName()

      createServer(serverName, service)
      val serverChannel = getChannel(serverName)

      val writeHandlerServicetub: WriteSideHandlerServiceBlockingStub =
        new WriteSideHandlerServiceBlockingStub(serverChannel)

      // Let us create the sender of commands
      val commandSender: TestProbe[CommandReply] =
        createTestProbe[CommandReply]()

      val remoteCommandHandler: RemoteCommandHandler =
        RemoteCommandHandler(cosConfig.grpcConfig, writeHandlerServicetub)
      val remoteEventHandler: RemoteEventHandler = RemoteEventHandler(cosConfig.grpcConfig, writeHandlerServicetub)
      val shardIndex = 0
      val eventsAndStateProtosValidation: ProtosValidator =
        ProtosValidator(cosConfig.writeSideConfig)

      val aggregateRoot = AggregateRoot(persistenceId,
                                        shardIndex,
                                        cosConfig,
                                        remoteCommandHandler,
                                        remoteEventHandler,
                                        eventsAndStateProtosValidation
      )

      val aggregateRef: ActorRef[AggregateCommand] = spawn(aggregateRoot)

      val remoteCommand = RemoteCommand()
        .withCommand(command)
        .addHeaders(RemoteCommand.Header().withKey("header-1").withStringValue("header-value-1"))
        .withEntityId(aggregateId)

      aggregateRef ! AggregateCommand(
        SendCommand().withRemoteCommand(remoteCommand),
        commandSender.ref,
        Map.empty[String, Any]
      )

      commandSender.receiveMessage(replyTimeout) match {
        case CommandReply(Reply.State(value: StateWrapper), _) =>
          value.getState shouldBe resultingState
          value.getMeta.revisionNumber shouldBe priorMeta.revisionNumber + 1
          value.getMeta.entityId shouldBe aggregateId

        case x =>
          fail("unexpected message type")
      }
    }
    "return as expected with no event to persist" in {
      val aggregateId: String = UUID.randomUUID().toString
      val persistenceId: PersistenceId = PersistenceId("chiefofstate", aggregateId)
      val stateWrapper: StateWrapper = StateWrapper()
        .withState(any.Any.pack(Empty.defaultInstance))
        .withMeta(
          MetaData.defaultInstance.withEntityId(getEntityId(persistenceId))
        )
      val command: Any = Any.pack(OpenAccount())

      val request = HandleCommandRequest()
        .withCommand(command)
        .withPriorState(stateWrapper.getState)
        .withPriorEventMeta(stateWrapper.getMeta)

      val serviceImpl = mock[WriteSideHandlerServiceGrpc.WriteSideHandlerService]

      (serviceImpl.handleCommand _)
        .expects(request)
        .returning(Future.successful(HandleCommandResponse()))

      val service = WriteSideHandlerServiceGrpc.bindService(serviceImpl, global)

      val serverName = InProcessServerBuilder.generateName()

      createServer(serverName, service)
      val serverChannel = getChannel(serverName)

      val writeHandlerServicetub: WriteSideHandlerServiceBlockingStub =
        new WriteSideHandlerServiceBlockingStub(serverChannel)

      // Let us create the sender of commands
      val commandSender: TestProbe[CommandReply] =
        createTestProbe[CommandReply]()

      val remoteCommandHandler: RemoteCommandHandler =
        RemoteCommandHandler(cosConfig.grpcConfig, writeHandlerServicetub)
      val remoteEventHandler: RemoteEventHandler = RemoteEventHandler(cosConfig.grpcConfig, writeHandlerServicetub)
      val shardIndex = 0
      val eventsAndStateProtosValidation: ProtosValidator =
        ProtosValidator(cosConfig.writeSideConfig)

      val aggregateRoot = AggregateRoot(persistenceId,
                                        shardIndex,
                                        cosConfig,
                                        remoteCommandHandler,
                                        remoteEventHandler,
                                        eventsAndStateProtosValidation
      )

      val aggregateRef: ActorRef[AggregateCommand] = spawn(aggregateRoot)

      val remoteCommand = RemoteCommand()
        .withCommand(command)
        .addHeaders(RemoteCommand.Header().withKey("header-1").withStringValue("header-value-1"))
        .withEntityId(aggregateId)

      aggregateRef ! AggregateCommand(
        SendCommand().withRemoteCommand(remoteCommand),
        commandSender.ref,
        Map.empty[String, Any]
      )

      commandSender.receiveMessage(replyTimeout) match {
        case CommandReply(Reply.State(value: StateWrapper), _) =>
          value.getState shouldBe Any.pack(Empty.defaultInstance)
          value.getMeta.revisionNumber shouldBe 0
          value.getMeta.entityId shouldBe aggregateId

        case _ => fail("unexpected message type")
      }
    }
    "return a failure when an empty command is sent" in {
      val aggregateId: String = UUID.randomUUID().toString
      val persistenceId: PersistenceId = PersistenceId("chiefofstate", aggregateId)

      val serviceImpl = mock[WriteSideHandlerServiceGrpc.WriteSideHandlerService]
      val service = WriteSideHandlerServiceGrpc.bindService(serviceImpl, global)

      val serverName = InProcessServerBuilder.generateName()

      createServer(serverName, service)
      val serverChannel = getChannel(serverName)

      val writeHandlerServicetub: WriteSideHandlerServiceBlockingStub =
        new WriteSideHandlerServiceBlockingStub(serverChannel)

      // Let us create the sender of commands
      val commandSender: TestProbe[CommandReply] =
        createTestProbe[CommandReply]()

      val remoteCommandHandler: RemoteCommandHandler =
        RemoteCommandHandler(cosConfig.grpcConfig, writeHandlerServicetub)

      val remoteEventHandler: RemoteEventHandler = RemoteEventHandler(cosConfig.grpcConfig, writeHandlerServicetub)
      val shardIndex = 0
      val eventsAndStateProtosValidation: ProtosValidator =
        ProtosValidator(cosConfig.writeSideConfig)

      val aggregateRoot = AggregateRoot(persistenceId,
                                        shardIndex,
                                        cosConfig,
                                        remoteCommandHandler,
                                        remoteEventHandler,
                                        eventsAndStateProtosValidation
      )

      val aggregateRef: ActorRef[AggregateCommand] = spawn(aggregateRoot)

      aggregateRef ! AggregateCommand(
        SendCommand().withType(Type.Empty),
        commandSender.ref,
        Map.empty[String, Any]
      )

      commandSender.receiveMessage(replyTimeout) match {
        case CommandReply(Reply.Error(status), _) =>
          status.code shouldBe (Status.Code.INTERNAL.value)
          Option(status.message) shouldBe Some("something really bad happens...")
        case _ => fail("unexpected message type")
      }

    }
    "return a failure when command handler failed" in {
      val aggregateId: String = UUID.randomUUID().toString
      val persistenceId: PersistenceId = PersistenceId("chiefofstate", aggregateId)
      val command: Any = Any.pack(OpenAccount())

      val serviceImpl = mock[WriteSideHandlerServiceGrpc.WriteSideHandlerService]

      (serviceImpl.handleCommand _)
        .expects(*)
        .returning(Future.failed(Status.INTERNAL.withDescription("oops").asException()))

      val service = WriteSideHandlerServiceGrpc.bindService(serviceImpl, global)

      val serverName = InProcessServerBuilder.generateName()

      createServer(serverName, service)
      val serverChannel = getChannel(serverName)

      val writeHandlerServicetub: WriteSideHandlerServiceBlockingStub =
        new WriteSideHandlerServiceBlockingStub(serverChannel)

      // Let us create the sender of commands
      val commandSender: TestProbe[CommandReply] =
        createTestProbe[CommandReply]()

      val remoteCommandHandler: RemoteCommandHandler =
        RemoteCommandHandler(cosConfig.grpcConfig, writeHandlerServicetub)
      val remoteEventHandler: RemoteEventHandler = RemoteEventHandler(cosConfig.grpcConfig, writeHandlerServicetub)
      val shardIndex = 0
      val eventsAndStateProtosValidation: ProtosValidator =
        ProtosValidator(cosConfig.writeSideConfig)

      val aggregateRoot = AggregateRoot(persistenceId,
                                        shardIndex,
                                        cosConfig,
                                        remoteCommandHandler,
                                        remoteEventHandler,
                                        eventsAndStateProtosValidation
      )

      val aggregateRef: ActorRef[AggregateCommand] = spawn(aggregateRoot)

      val remoteCommand = RemoteCommand()
        .withCommand(command)
        .addHeaders(RemoteCommand.Header().withKey("header-1").withStringValue("header-value-1"))
        .withEntityId(aggregateId)

      aggregateRef ! AggregateCommand(
        SendCommand().withRemoteCommand(remoteCommand),
        commandSender.ref,
        Map.empty[String, Any]
      )

      commandSender.receiveMessage(replyTimeout) match {
        case CommandReply(Reply.Error(status), _) =>
          status.code shouldBe (Status.Code.INTERNAL.value)
          Option(status.message) shouldBe (Some("oops"))

        case _ => fail("unexpected message type")
      }
    }
    "return a failure when event handler failed" in {
      val aggregateId: String = UUID.randomUUID().toString
      val persistenceId: PersistenceId = PersistenceId("chiefofstate", aggregateId)
      val command: Any = Any.pack(OpenAccount())

      val serviceImpl = mock[WriteSideHandlerServiceGrpc.WriteSideHandlerService]

      (serviceImpl.handleCommand _)
        .expects(*)
        .returning(Future.successful(HandleCommandResponse().withEvent(Any.pack(AccountOpened()))))

      (serviceImpl.handleEvent _)
        .expects(*)
        .returning(Future.failed(Status.UNKNOWN.asException()))

      val service = WriteSideHandlerServiceGrpc.bindService(serviceImpl, global)

      val serverName = InProcessServerBuilder.generateName()

      createServer(serverName, service)
      val serverChannel = getChannel(serverName)

      val writeHandlerServicetub: WriteSideHandlerServiceBlockingStub =
        new WriteSideHandlerServiceBlockingStub(serverChannel)

      // Let us create the sender of commands
      val commandSender: TestProbe[CommandReply] =
        createTestProbe[CommandReply]()

      val remoteCommandHandler: RemoteCommandHandler =
        RemoteCommandHandler(cosConfig.grpcConfig, writeHandlerServicetub)
      val remoteEventHandler: RemoteEventHandler = RemoteEventHandler(cosConfig.grpcConfig, writeHandlerServicetub)
      val shardIndex = 0
      val eventsAndStateProtosValidation: ProtosValidator =
        ProtosValidator(cosConfig.writeSideConfig)

      val aggregateRoot = AggregateRoot(persistenceId,
                                        shardIndex,
                                        cosConfig,
                                        remoteCommandHandler,
                                        remoteEventHandler,
                                        eventsAndStateProtosValidation
      )

      val aggregateRef: ActorRef[AggregateCommand] = spawn(aggregateRoot)

      val remoteCommand = RemoteCommand()
        .withCommand(command)
        .addHeaders(RemoteCommand.Header().withKey("header-1").withStringValue("header-value-1"))
        .withEntityId(aggregateId)

      aggregateRef ! AggregateCommand(
        SendCommand().withRemoteCommand(remoteCommand),
        commandSender.ref,
        Map.empty[String, Any]
      )

      commandSender.receiveMessage(replyTimeout) match {
        case CommandReply(Reply.Error(status), _) =>
          status.code shouldBe (Status.Code.UNKNOWN.value)
          Option(status.message) shouldBe (Some(""))

        case _ => fail("unexpected message type")
      }
    }
    "return a failure when an invalid event is received" in {
      val config: Config = ConfigFactory.parseString(s"""
            akka.cluster.sharding.number-of-shards = 1
            chiefofstate {
             	service-name = "chiefofstate"
              ask-timeout = 5
              snapshot-criteria {
                disable-snapshot = false
                retention-frequency = 1
                retention-number = 1
                delete-events-on-snapshot = true
              }
              events {
                tagname: "cos"
              }
              grpc {
                client {
                  deadline-timeout = 3000
                }
                server {
                  address = "0.0.0.0"
                  port = 9000
                }
              }
              write-side {
                host = "localhost"
                port = 6000
                use-tls = false
                enable-protos-validation = true
                states-protos = ""
                events-protos = ""
                propagated-headers = ""
              }
              read-side {
                # set this value to true whenever a readSide config is set
                enabled = false
              }
              telemetry {
                namespace = ""
                otlp_endpoint = ""
                trace_propagators = "b3multi"
              }
            }
          """)
      val mainConfig = CosConfig(config)

      val aggregateId: String = UUID.randomUUID().toString
      val persistenceId: PersistenceId = PersistenceId("chiefofstate", aggregateId)
      val command: Any = Any.pack(OpenAccount())

      val serviceImpl = mock[WriteSideHandlerServiceGrpc.WriteSideHandlerService]

      (serviceImpl.handleCommand _)
        .expects(*)
        .returning(Future.successful(HandleCommandResponse().withEvent(Any.pack(AccountOpened()))))

      val service = WriteSideHandlerServiceGrpc.bindService(serviceImpl, global)

      val serverName = InProcessServerBuilder.generateName()

      createServer(serverName, service)
      val serverChannel = getChannel(serverName)

      val writeHandlerServicetub: WriteSideHandlerServiceBlockingStub =
        new WriteSideHandlerServiceBlockingStub(serverChannel)

      // Let us create the sender of commands
      val commandSender: TestProbe[CommandReply] =
        createTestProbe[CommandReply]()

      val remoteCommandHandler: RemoteCommandHandler =
        RemoteCommandHandler(mainConfig.grpcConfig, writeHandlerServicetub)
      val remoteEventHandler: RemoteEventHandler = RemoteEventHandler(mainConfig.grpcConfig, writeHandlerServicetub)
      val shardIndex = 0
      val eventsAndStateProtosValidation: ProtosValidator =
        ProtosValidator(mainConfig.writeSideConfig)

      val aggregateRoot = AggregateRoot(persistenceId,
                                        shardIndex,
                                        mainConfig,
                                        remoteCommandHandler,
                                        remoteEventHandler,
                                        eventsAndStateProtosValidation
      )

      val aggregateRef: ActorRef[AggregateCommand] = spawn(aggregateRoot)

      val remoteCommand = RemoteCommand()
        .withCommand(command)
        .addHeaders(RemoteCommand.Header().withKey("header-1").withStringValue("header-value-1"))
        .withEntityId(aggregateId)

      aggregateRef ! AggregateCommand(
        SendCommand().withRemoteCommand(remoteCommand),
        commandSender.ref,
        Map.empty[String, Any]
      )

      commandSender.receiveMessage(replyTimeout) match {
        case CommandReply(Reply.Error(status), _) =>
          status.code shouldBe (Status.Code.INVALID_ARGUMENT.value)
          Option(status.message) shouldBe (Some(
            "invalid event: type.googleapis.com/chief_of_state.v1.AccountOpened"
          ))

        case _ => fail("unexpected message type")
      }

    }
    "return a failure when an invalid state is received" in {
      val config: Config = ConfigFactory.parseString(s"""
            akka.cluster.sharding.number-of-shards = 1
            chiefofstate {
             	service-name = "chiefofstate"
              ask-timeout = 5
              snapshot-criteria {
                disable-snapshot = true
                retention-frequency = 1
                retention-number = 1
                delete-events-on-snapshot = false
              }
              events {
                tagname: "cos"
              }
              grpc {
                client {
                  deadline-timeout = 3000
                }
                server {
                  address = "0.0.0.0"
                  port = 9000
                }
              }
              write-side {
                host = "localhost"
                port = 6000
                use-tls = false
                enable-protos-validation = true
                states-protos = ""
                events-protos = "chief_of_state.v1.AccountOpened"
                propagated-headers = ""
              }
              read-side {
                # set this value to true whenever a readSide config is set
                enabled = false
              }
              telemetry {
                namespace = ""
                otlp_endpoint = ""
                trace_propagators = "b3multi"
              }
            }
          """)
      val mainConfig = CosConfig(config)

      val aggregateId: String = UUID.randomUUID().toString
      val persistenceId: PersistenceId = PersistenceId("chiefofstate", aggregateId)
      val command: Any = Any.pack(OpenAccount())
      val event: AccountOpened = AccountOpened()
      val state: Account = Account().withAccountUuid(aggregateId)
      val resultingState = com.google.protobuf.any.Any.pack(state.withBalance(200))

      val serviceImpl = mock[WriteSideHandlerServiceGrpc.WriteSideHandlerService]

      (serviceImpl.handleCommand _)
        .expects(*)
        .returning(Future.successful(HandleCommandResponse().withEvent(Any.pack(event))))

      (serviceImpl.handleEvent _)
        .expects(*)
        .returning(Future.successful(HandleEventResponse().withResultingState(resultingState)))

      val service = WriteSideHandlerServiceGrpc.bindService(serviceImpl, global)

      val serverName = InProcessServerBuilder.generateName()

      createServer(serverName, service)
      val serverChannel = getChannel(serverName)

      val writeHandlerServicetub: WriteSideHandlerServiceBlockingStub =
        new WriteSideHandlerServiceBlockingStub(serverChannel)

      // Let us create the sender of commands
      val commandSender: TestProbe[CommandReply] =
        createTestProbe[CommandReply]()

      val remoteCommandHandler: RemoteCommandHandler =
        RemoteCommandHandler(mainConfig.grpcConfig, writeHandlerServicetub)
      val remoteEventHandler: RemoteEventHandler = RemoteEventHandler(mainConfig.grpcConfig, writeHandlerServicetub)
      val shardIndex = 0
      val eventsAndStateProtosValidation: ProtosValidator =
        ProtosValidator(mainConfig.writeSideConfig)

      val aggregateRoot = AggregateRoot(persistenceId,
                                        shardIndex,
                                        mainConfig,
                                        remoteCommandHandler,
                                        remoteEventHandler,
                                        eventsAndStateProtosValidation
      )

      val aggregateRef: ActorRef[AggregateCommand] = spawn(aggregateRoot)

      val remoteCommand = RemoteCommand()
        .withCommand(command)
        .addHeaders(RemoteCommand.Header().withKey("header-1").withStringValue("header-value-1"))
        .withEntityId(aggregateId)

      aggregateRef ! AggregateCommand(
        SendCommand().withRemoteCommand(remoteCommand),
        commandSender.ref,
        Map.empty[String, Any]
      )

      commandSender.receiveMessage(replyTimeout) match {
        case CommandReply(Reply.Error(status), _) =>
          status.code shouldBe (Status.Code.INVALID_ARGUMENT.value)
          Option(status.message) shouldBe (Some(
            "invalid state: type.googleapis.com/chief_of_state.v1.Account"
          ))

        case _ => fail("unexpected message type")
      }

    }

    "return a failure when an empty state is received" in {
      val config: Config = ConfigFactory.parseString(s"""
            akka.cluster.sharding.number-of-shards = 1
            chiefofstate {
             	service-name = "chiefofstate"
              ask-timeout = 5
              snapshot-criteria {
                disable-snapshot = true
                retention-frequency = 1
                retention-number = 1
                delete-events-on-snapshot = false
              }
              events {
                tagname: "cos"
              }
              grpc {
                client {
                  deadline-timeout = 3000
                }
                server {
                  address = "0.0.0.0"
                  port = 9000
                }
              }
              write-side {
                host = "localhost"
                port = 6000
                use-tls = false
                enable-protos-validation = true
                states-protos = ""
                events-protos = "chief_of_state.v1.AccountOpened"
                propagated-headers = ""
              }
              read-side {
                # set this value to true whenever a readSide config is set
                enabled = false
              }
              telemetry {
                namespace = ""
                otlp_endpoint = ""
                trace_propagators = "b3multi"
              }
            }
          """)
      val mainConfig = CosConfig(config)

      val aggregateId: String = UUID.randomUUID().toString
      val persistenceId: PersistenceId = PersistenceId("chiefofstate", aggregateId)
      val command: Any = Any.pack(OpenAccount())
      val event: AccountOpened = AccountOpened()

      val serviceImpl = mock[WriteSideHandlerServiceGrpc.WriteSideHandlerService]

      (serviceImpl.handleCommand _)
        .expects(*)
        .returning(Future.successful(HandleCommandResponse().withEvent(Any.pack(event))))

      (serviceImpl.handleEvent _)
        .expects(*)
        .returning(Future.successful(HandleEventResponse()))

      val service = WriteSideHandlerServiceGrpc.bindService(serviceImpl, global)

      val serverName = InProcessServerBuilder.generateName()

      createServer(serverName, service)
      val serverChannel = getChannel(serverName)

      val writeHandlerServicetub: WriteSideHandlerServiceBlockingStub =
        new WriteSideHandlerServiceBlockingStub(serverChannel)

      // Let us create the sender of commands
      val commandSender: TestProbe[CommandReply] =
        createTestProbe[CommandReply]()

      val remoteCommandHandler: RemoteCommandHandler =
        RemoteCommandHandler(mainConfig.grpcConfig, writeHandlerServicetub)
      val remoteEventHandler: RemoteEventHandler = RemoteEventHandler(mainConfig.grpcConfig, writeHandlerServicetub)
      val shardIndex = 0
      val eventsAndStateProtosValidation: ProtosValidator =
        ProtosValidator(mainConfig.writeSideConfig)

      val aggregateRoot = AggregateRoot(persistenceId,
                                        shardIndex,
                                        mainConfig,
                                        remoteCommandHandler,
                                        remoteEventHandler,
                                        eventsAndStateProtosValidation
      )

      val aggregateRef: ActorRef[AggregateCommand] = spawn(aggregateRoot)

      val remoteCommand = RemoteCommand()
        .withCommand(command)
        .addHeaders(RemoteCommand.Header().withKey("header-1").withStringValue("header-value-1"))
        .withEntityId(aggregateId)

      aggregateRef ! AggregateCommand(
        SendCommand().withRemoteCommand(remoteCommand),
        commandSender.ref,
        Map.empty[String, Any]
      )

      commandSender.receiveMessage(replyTimeout) match {
        case CommandReply(Reply.Error(status), _) =>
          status.code shouldBe (Status.Code.INVALID_ARGUMENT.value)
          Option(status.message) shouldBe (Some(
            "event handler replied with empty state"
          ))

        case _ => fail("unexpected message type")
      }

    }
  }

  ".getStateCommand" should {
    "return as expected" in {
      val aggregateId: String = UUID.randomUUID().toString
      val persistenceId: PersistenceId = PersistenceId("chiefofstate", aggregateId)
      val state: Account = Account().withAccountUuid(aggregateId)
      val command: Any = Any.pack(OpenAccount())
      val event: AccountOpened = AccountOpened()
      val resultingState = com.google.protobuf.any.Any.pack(state.withBalance(200))

      val serviceImpl = mock[WriteSideHandlerServiceGrpc.WriteSideHandlerService]

      (serviceImpl.handleCommand _)
        .expects(*)
        .returning(Future.successful(HandleCommandResponse().withEvent(Any.pack(event))))

      (serviceImpl.handleEvent _)
        .expects(*)
        .returning(Future.successful(HandleEventResponse().withResultingState(resultingState)))

      val service = WriteSideHandlerServiceGrpc.bindService(serviceImpl, global)

      val serverName = InProcessServerBuilder.generateName()

      createServer(serverName, service)
      val serverChannel = getChannel(serverName)

      val writeHandlerServicetub: WriteSideHandlerServiceBlockingStub =
        new WriteSideHandlerServiceBlockingStub(serverChannel)
      // Let us create the sender of commands
      val commandSender: TestProbe[CommandReply] =
        createTestProbe[CommandReply]()

      val remoteCommandHandler: RemoteCommandHandler =
        RemoteCommandHandler(cosConfig.grpcConfig, writeHandlerServicetub)
      val remoteEventHandler: RemoteEventHandler = RemoteEventHandler(cosConfig.grpcConfig, writeHandlerServicetub)
      val shardIndex = 0
      val eventsAndStateProtosValidation: ProtosValidator =
        ProtosValidator(cosConfig.writeSideConfig)

      val aggregateRoot = AggregateRoot(persistenceId,
                                        shardIndex,
                                        cosConfig,
                                        remoteCommandHandler,
                                        remoteEventHandler,
                                        eventsAndStateProtosValidation
      )
      val aggregateRef: ActorRef[AggregateCommand] = spawn(aggregateRoot)

      val remoteCommand = RemoteCommand()
        .withCommand(command)
        .addHeaders(RemoteCommand.Header().withKey("header-1").withStringValue("header-value-1"))
        .withEntityId(aggregateId)

      aggregateRef ! AggregateCommand(
        SendCommand().withRemoteCommand(remoteCommand),
        commandSender.ref,
        Map.empty[String, Any]
      )
      commandSender.receiveMessage(replyTimeout) match {
        case CommandReply(Reply.State(value: StateWrapper), _) =>
          val account: Account = value.getState.unpack[Account]
          account.accountUuid shouldBe aggregateId
          account.balance shouldBe 200
          value.getMeta.revisionNumber shouldBe 1
          value.getMeta.entityId shouldBe aggregateId

        case _ => fail("unexpected message type")
      }
      aggregateRef ! AggregateCommand(
        SendCommand().withGetStateCommand(
          GetStateCommand()
            .withEntityId(aggregateId)
        ),
        commandSender.ref,
        Map.empty[String, Any]
      )

      commandSender.receiveMessage(replyTimeout) match {
        case CommandReply(reply, _) =>
          reply match {
            case Reply.State(value: StateWrapper) =>
              val account: Account = value.getState.unpack[Account]
              account.accountUuid shouldBe aggregateId
              account.balance shouldBe 200
              value.getMeta.revisionNumber shouldBe 1
              value.getMeta.entityId shouldBe aggregateId

            case _ => fail("unexpected message state")
          }
        case _ => fail("unexpected message type")
      }
    }
    "return a failure when there is no entity as expected" in {
      val aggregateId: String = UUID.randomUUID().toString
      val persistenceId: PersistenceId = PersistenceId("chiefofstate", aggregateId)

      // Let us create the sender of commands
      val commandSender: TestProbe[CommandReply] =
        createTestProbe[CommandReply]()

      val serviceImpl = mock[WriteSideHandlerServiceGrpc.WriteSideHandlerService]
      val service = WriteSideHandlerServiceGrpc.bindService(serviceImpl, global)
      val serverName = InProcessServerBuilder.generateName()

      createServer(serverName, service)
      val serverChannel = getChannel(serverName)

      val writeHandlerServicetub: WriteSideHandlerServiceBlockingStub =
        new WriteSideHandlerServiceBlockingStub(serverChannel)

      val remoteCommandHandler: RemoteCommandHandler =
        RemoteCommandHandler(cosConfig.grpcConfig, writeHandlerServicetub)

      val remoteEventHandler: RemoteEventHandler = RemoteEventHandler(cosConfig.grpcConfig, writeHandlerServicetub)
      val shardIndex = 0
      val eventsAndStateProtosValidation: ProtosValidator =
        ProtosValidator(cosConfig.writeSideConfig)

      val aggregateRoot = AggregateRoot(persistenceId,
                                        shardIndex,
                                        cosConfig,
                                        remoteCommandHandler,
                                        remoteEventHandler,
                                        eventsAndStateProtosValidation
      )
      val aggregateRef: ActorRef[AggregateCommand] = spawn(aggregateRoot)

      aggregateRef ! AggregateCommand(
        SendCommand().withGetStateCommand(
          GetStateCommand()
            .withEntityId(aggregateId)
        ),
        commandSender.ref,
        Map.empty[String, Any]
      )

      commandSender.receiveMessage(replyTimeout) match {
        case CommandReply(reply, _) =>
          reply match {
            case Reply.Error(status) =>
              status.code shouldBe (Status.Code.NOT_FOUND.value)
            case _ => fail("unexpected message state")
          }
        case _ => fail("unexpected message type")
      }
    }
  }
}
