package com.namely.chiefofstate.test

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.persistence.typed.PersistenceId
import com.google.protobuf.any
import com.google.protobuf.any.Any
import com.google.protobuf.empty.Empty
import com.namely.chiefofstate._
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
import io.grpc.{ManagedChannel, Status}
import io.grpc.netty.NettyChannelBuilder
import org.grpcmock.GrpcMock
import org.grpcmock.GrpcMock._

import scala.concurrent.duration.FiniteDuration

class AggegrateRootSpec extends BaseActorSpec(s"""
      akka.cluster.sharding.number-of-shards = 1
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "tmp/snapshot"
    """) {

  var serverChannel: ManagedChannel = null
  var cosConfig: CosConfig = null
  val actorSystem: ActorSystem[Nothing] = testKit.system
  val replyTimeout: FiniteDuration = FiniteDuration(30, TimeUnit.SECONDS)

  override def beforeAll(): Unit = {
    GrpcMock.configureFor(grpcMock(6000).build().start())
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
                  port = 9000
                }
              }
              write-side {
                host = "localhost"
                port = 6000
                enable-protos-validation = false
                states-protos = ""
                events-protos = ""
                propagated-headers = ""
              }
              read-side {
                create-stores {
                  auto = true
                }
                # set this value to true whenever a readSide config is set
                enabled = false
              }
            }
          """)
    cosConfig = CosConfig(config)
  }

  override def beforeEach(): Unit = {
    GrpcMock.resetMappings()
    serverChannel = NettyChannelBuilder
      .forAddress("localhost", getGlobalPort)
      .usePlaintext()
      .build()
  }

  override def afterEach(): Unit = {
    serverChannel.shutdownNow()
  }

  ".tags" should {
    "return the list of possible tags" in {
      val eventsConfig: EventsConfig = EventsConfig("tag", 10)
      val tags: Seq[String] = AggregateRoot.tags(eventsConfig)
      tags.size shouldBe 10
      tags should contain("tag7")
    }
  }

  ".getEntityId" should {
    "return the actual entity id" in {
      val expected: String = "123"
      val persistenceId: PersistenceId = PersistenceId("chiefofstate", "123")
      AggregateRoot.getEntityId(persistenceId) shouldBe expected
    }
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
      val aggregateId: String = UUID.randomUUID().toString
      val persistenceId: PersistenceId = PersistenceId("chiefofstate", aggregateId)
      val state: Account = Account().withAccountUuid(aggregateId)
      val stateWrapper: StateWrapper = StateWrapper()
        .withState(any.Any.pack(Empty.defaultInstance))
        .withMeta(
          MetaData.defaultInstance.withEntityId(getEntityId(persistenceId))
        )
      val command: Any = Any.pack(OpenAccount())
      val event: AccountOpened = AccountOpened()

      val resultingState = com.google.protobuf.any.Any.pack(state.withBalance(200))

      stubFor(
        unaryMethod(WriteSideHandlerServiceGrpc.METHOD_HANDLE_COMMAND)
          .withRequest(
            HandleCommandRequest()
              .withCommand(command)
              .withPriorState(stateWrapper.getState)
              .withPriorEventMeta(stateWrapper.getMeta)
          )
          .withHeader("header-1", "header-value-1")
          .willReturn(
            response(HandleCommandResponse().withEvent(Any.pack(event)))
          )
      )

      stubFor(
        unaryMethod(WriteSideHandlerServiceGrpc.METHOD_HANDLE_EVENT)
          .withRequest(
            HandleEventRequest()
              .withPriorState(stateWrapper.getState)
              .withEventMeta(stateWrapper.getMeta)
              .withEvent(Any.pack(event))
          )
          .willReturn(
            response(HandleEventResponse().withResultingState(resultingState))
          )
      )

      val writeHandlerServicetub: WriteSideHandlerServiceBlockingStub =
        new WriteSideHandlerServiceBlockingStub(serverChannel)

      // Let us create the sender of commands
      val commandSender: TestProbe[CommandReply] =
        createTestProbe[CommandReply]()

      val remoteCommandHandler: RemoteCommandHandler =
        RemoteCommandHandler(cosConfig.grpcConfig, writeHandlerServicetub)
      val remoteEventHandler: RemoteEventHandler = RemoteEventHandler(cosConfig.grpcConfig, writeHandlerServicetub)
      val shardIndex = 0
      val eventsAndStateProtosValidation: EventsAndStateProtosValidation =
        EventsAndStateProtosValidation(cosConfig.writeSideConfig)

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
        case CommandReply(reply, _) =>
          reply match {
            case Reply.Empty => fail("unexpected message state")
            case Reply.State(value: StateWrapper) =>
              val account: Account = value.getState.unpack[Account]
              account.accountUuid shouldBe aggregateId
              account.balance shouldBe 200
              value.getMeta.revisionNumber shouldBe 1
              value.getMeta.entityId shouldBe aggregateId
            case Reply.Failure(failureResponse: FailureResponse) => fail(s"unexpected message state $failureResponse")
          }
        case _ => fail("unexpected message type")
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

      stubFor(
        unaryMethod(WriteSideHandlerServiceGrpc.METHOD_HANDLE_COMMAND)
          .withRequest(
            HandleCommandRequest()
              .withCommand(command)
              .withPriorState(stateWrapper.getState)
              .withPriorEventMeta(stateWrapper.getMeta)
          )
          .withHeader("header-1", "header-value-1")
          .willReturn(
            response(HandleCommandResponse())
          )
      )

      val writeHandlerServicetub: WriteSideHandlerServiceBlockingStub =
        new WriteSideHandlerServiceBlockingStub(serverChannel)

      // Let us create the sender of commands
      val commandSender: TestProbe[CommandReply] =
        createTestProbe[CommandReply]()

      val remoteCommandHandler: RemoteCommandHandler =
        RemoteCommandHandler(cosConfig.grpcConfig, writeHandlerServicetub)
      val remoteEventHandler: RemoteEventHandler = RemoteEventHandler(cosConfig.grpcConfig, writeHandlerServicetub)
      val shardIndex = 0
      val eventsAndStateProtosValidation: EventsAndStateProtosValidation =
        EventsAndStateProtosValidation(cosConfig.writeSideConfig)

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
        case CommandReply(reply, _) =>
          reply match {
            case Reply.Empty => fail("unexpected message state")
            case Reply.State(value: StateWrapper) =>
              value.getState shouldBe Any.pack(Empty.defaultInstance)
              value.getMeta.revisionNumber shouldBe 0
              value.getMeta.entityId shouldBe aggregateId
            case Reply.Failure(failureResponse: FailureResponse) => fail(s"unexpected message state $failureResponse")
          }
        case _ => fail("unexpected message type")
      }
    }
    "return a failure when an empty command is sent" in {
      val aggregateId: String = UUID.randomUUID().toString
      val persistenceId: PersistenceId = PersistenceId("chiefofstate", aggregateId)
      val state: Account = Account().withAccountUuid(aggregateId)
      val stateWrapper: StateWrapper = StateWrapper()
        .withState(any.Any.pack(Empty.defaultInstance))
        .withMeta(
          MetaData.defaultInstance.withEntityId(getEntityId(persistenceId))
        )
      val command: Any = Any.pack(OpenAccount())
      val event: AccountOpened = AccountOpened()

      val resultingState = com.google.protobuf.any.Any.pack(state.withBalance(200))

      stubFor(
        unaryMethod(WriteSideHandlerServiceGrpc.METHOD_HANDLE_COMMAND)
          .withRequest(
            HandleCommandRequest()
              .withCommand(command)
              .withPriorState(stateWrapper.getState)
              .withPriorEventMeta(stateWrapper.getMeta)
          )
          .withHeader("header-1", "header-value-1")
          .willReturn(
            response(HandleCommandResponse().withEvent(Any.pack(event)))
          )
      )

      stubFor(
        unaryMethod(WriteSideHandlerServiceGrpc.METHOD_HANDLE_EVENT)
          .withRequest(
            HandleEventRequest()
              .withPriorState(stateWrapper.getState)
              .withEventMeta(stateWrapper.getMeta)
              .withEvent(Any.pack(event))
          )
          .willReturn(
            response(HandleEventResponse().withResultingState(resultingState))
          )
      )

      val writeHandlerServicetub: WriteSideHandlerServiceBlockingStub =
        new WriteSideHandlerServiceBlockingStub(serverChannel)

      // Let us create the sender of commands
      val commandSender: TestProbe[CommandReply] =
        createTestProbe[CommandReply]()

      val remoteCommandHandler: RemoteCommandHandler =
        RemoteCommandHandler(cosConfig.grpcConfig, writeHandlerServicetub)
      val remoteEventHandler: RemoteEventHandler = RemoteEventHandler(cosConfig.grpcConfig, writeHandlerServicetub)
      val shardIndex = 0
      val eventsAndStateProtosValidation: EventsAndStateProtosValidation =
        EventsAndStateProtosValidation(cosConfig.writeSideConfig)

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
        case CommandReply(reply, _) =>
          reply match {
            case Reply.Empty    => fail("unexpected message state")
            case Reply.State(_) => fail("unexpected message state")
            case Reply.Failure(failureResponse) =>
              failureResponse shouldBe FailureResponse().withCritical("something really bad happens...")
          }
        case _ => fail("unexpected message type")
      }

    }
    "return a failure when command handler failed" in {
      val aggregateId: String = UUID.randomUUID().toString
      val persistenceId: PersistenceId = PersistenceId("chiefofstate", aggregateId)
      val state: Account = Account().withAccountUuid(aggregateId)
      val stateWrapper: StateWrapper = StateWrapper()
        .withState(any.Any.pack(Empty.defaultInstance))
        .withMeta(
          MetaData.defaultInstance.withEntityId(getEntityId(persistenceId))
        )
      val command: Any = Any.pack(OpenAccount())
      val event: AccountOpened = AccountOpened()

      val resultingState = com.google.protobuf.any.Any.pack(state.withBalance(200))

      stubFor(
        unaryMethod(WriteSideHandlerServiceGrpc.METHOD_HANDLE_COMMAND)
          .withRequest(
            HandleCommandRequest()
              .withCommand(command)
              .withPriorState(stateWrapper.getState)
              .withPriorEventMeta(stateWrapper.getMeta)
          )
          .withHeader("header-1", "header-value-1")
          .willReturn(
            statusException(Status.INTERNAL)
          )
      )

      stubFor(
        unaryMethod(WriteSideHandlerServiceGrpc.METHOD_HANDLE_EVENT)
          .withRequest(
            HandleEventRequest()
              .withPriorState(stateWrapper.getState)
              .withEventMeta(stateWrapper.getMeta)
              .withEvent(Any.pack(event))
          )
          .willReturn(
            response(HandleEventResponse().withResultingState(resultingState))
          )
      )

      val writeHandlerServicetub: WriteSideHandlerServiceBlockingStub =
        new WriteSideHandlerServiceBlockingStub(serverChannel)

      // Let us create the sender of commands
      val commandSender: TestProbe[CommandReply] =
        createTestProbe[CommandReply]()

      val remoteCommandHandler: RemoteCommandHandler =
        RemoteCommandHandler(cosConfig.grpcConfig, writeHandlerServicetub)
      val remoteEventHandler: RemoteEventHandler = RemoteEventHandler(cosConfig.grpcConfig, writeHandlerServicetub)
      val shardIndex = 0
      val eventsAndStateProtosValidation: EventsAndStateProtosValidation =
        EventsAndStateProtosValidation(cosConfig.writeSideConfig)

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
        case CommandReply(reply, _) =>
          reply match {
            case Reply.Empty    => fail("unexpected message state")
            case Reply.State(_) => fail("unexpected message state")
            case Reply.Failure(failureResponse) =>
              failureResponse shouldBe FailureResponse().withCritical(
                "[ChiefOfState] command handler failure: INTERNAL"
              )
          }
        case _ => fail("unexpected message type")
      }
    }
    "return a failure when event handler failed" in {
      val aggregateId: String = UUID.randomUUID().toString
      val persistenceId: PersistenceId = PersistenceId("chiefofstate", aggregateId)
      val stateWrapper: StateWrapper = StateWrapper()
        .withState(any.Any.pack(Empty.defaultInstance))
        .withMeta(
          MetaData.defaultInstance.withEntityId(getEntityId(persistenceId))
        )
      val command: Any = Any.pack(OpenAccount())
      val event: AccountOpened = AccountOpened()

      stubFor(
        unaryMethod(WriteSideHandlerServiceGrpc.METHOD_HANDLE_COMMAND)
          .withRequest(
            HandleCommandRequest()
              .withCommand(command)
              .withPriorState(stateWrapper.getState)
              .withPriorEventMeta(stateWrapper.getMeta)
          )
          .withHeader("header-1", "header-value-1")
          .willReturn(
            response(HandleCommandResponse().withEvent(Any.pack(event)))
          )
      )

      stubFor(
        unaryMethod(WriteSideHandlerServiceGrpc.METHOD_HANDLE_EVENT)
          .withRequest(
            HandleEventRequest()
              .withPriorState(stateWrapper.getState)
              .withEventMeta(stateWrapper.getMeta)
              .withEvent(Any.pack(event))
          )
          .willReturn(
            statusException(Status.UNKNOWN)
          )
      )

      val writeHandlerServicetub: WriteSideHandlerServiceBlockingStub =
        new WriteSideHandlerServiceBlockingStub(serverChannel)

      // Let us create the sender of commands
      val commandSender: TestProbe[CommandReply] =
        createTestProbe[CommandReply]()

      val remoteCommandHandler: RemoteCommandHandler =
        RemoteCommandHandler(cosConfig.grpcConfig, writeHandlerServicetub)
      val remoteEventHandler: RemoteEventHandler = RemoteEventHandler(cosConfig.grpcConfig, writeHandlerServicetub)
      val shardIndex = 0
      val eventsAndStateProtosValidation: EventsAndStateProtosValidation =
        EventsAndStateProtosValidation(cosConfig.writeSideConfig)

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
        case CommandReply(reply, _) =>
          reply match {
            case Reply.Empty    => fail("unexpected message state")
            case Reply.State(_) => fail("unexpected message state")
            case Reply.Failure(failureResponse) =>
              failureResponse shouldBe FailureResponse().withCritical(
                "[ChiefOfState] event handler failure: UNKNOWN"
              )
          }
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
                  port = 9000
                }
              }
              write-side {
                host = "localhost"
                port = 6000
                enable-protos-validation = true
                states-protos = ""
                events-protos = ""
                propagated-headers = ""
              }
              read-side {
                create-stores {
                  auto = true
                }
                # set this value to true whenever a readSide config is set
                enabled = false
              }
            }
          """)
      val mainConfig = CosConfig(config)

      val aggregateId: String = UUID.randomUUID().toString
      val persistenceId: PersistenceId = PersistenceId("chiefofstate", aggregateId)
      val stateWrapper: StateWrapper = StateWrapper()
        .withState(any.Any.pack(Empty.defaultInstance))
        .withMeta(
          MetaData.defaultInstance.withEntityId(getEntityId(persistenceId))
        )
      val command: Any = Any.pack(OpenAccount())
      val event: AccountOpened = AccountOpened()
      val state: Account = Account().withAccountUuid(aggregateId)
      val resultingState = com.google.protobuf.any.Any.pack(state.withBalance(200))
      stubFor(
        unaryMethod(WriteSideHandlerServiceGrpc.METHOD_HANDLE_COMMAND)
          .withRequest(
            HandleCommandRequest()
              .withCommand(command)
              .withPriorState(stateWrapper.getState)
              .withPriorEventMeta(stateWrapper.getMeta)
          )
          .withHeader("header-1", "header-value-1")
          .willReturn(
            response(HandleCommandResponse().withEvent(Any.pack(event)))
          )
      )

      stubFor(
        unaryMethod(WriteSideHandlerServiceGrpc.METHOD_HANDLE_EVENT)
          .withRequest(
            HandleEventRequest()
              .withPriorState(stateWrapper.getState)
              .withEventMeta(stateWrapper.getMeta)
              .withEvent(Any.pack(event))
          )
          .willReturn(
            response(HandleEventResponse().withResultingState(resultingState))
          )
      )

      val writeHandlerServicetub: WriteSideHandlerServiceBlockingStub =
        new WriteSideHandlerServiceBlockingStub(serverChannel)

      // Let us create the sender of commands
      val commandSender: TestProbe[CommandReply] =
        createTestProbe[CommandReply]()

      val remoteCommandHandler: RemoteCommandHandler =
        RemoteCommandHandler(mainConfig.grpcConfig, writeHandlerServicetub)
      val remoteEventHandler: RemoteEventHandler = RemoteEventHandler(mainConfig.grpcConfig, writeHandlerServicetub)
      val shardIndex = 0
      val eventsAndStateProtosValidation: EventsAndStateProtosValidation =
        EventsAndStateProtosValidation(mainConfig.writeSideConfig)

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
        case CommandReply(reply, _) =>
          reply match {
            case Reply.Empty    => fail("unexpected message state")
            case Reply.State(_) => fail("unexpected message state")
            case Reply.Failure(failureResponse) =>
              failureResponse shouldBe FailureResponse().withCritical(
                "[ChiefOfState] received unknown event type: type.googleapis.com/chief_of_state.v1.AccountOpened"
              )
          }
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
                  port = 9000
                }
              }
              write-side {
                host = "localhost"
                port = 6000
                enable-protos-validation = true
                states-protos = ""
                events-protos = "chief_of_state.v1.AccountOpened"
                propagated-headers = ""
              }
              read-side {
                create-stores {
                  auto = true
                }
                # set this value to true whenever a readSide config is set
                enabled = false
              }
            }
          """)
      val mainConfig = CosConfig(config)

      val aggregateId: String = UUID.randomUUID().toString
      val persistenceId: PersistenceId = PersistenceId("chiefofstate", aggregateId)
      val stateWrapper: StateWrapper = StateWrapper()
        .withState(any.Any.pack(Empty.defaultInstance))
        .withMeta(
          MetaData.defaultInstance.withEntityId(getEntityId(persistenceId))
        )
      val command: Any = Any.pack(OpenAccount())
      val event: AccountOpened = AccountOpened()
      val state: Account = Account().withAccountUuid(aggregateId)
      val resultingState = com.google.protobuf.any.Any.pack(state.withBalance(200))
      stubFor(
        unaryMethod(WriteSideHandlerServiceGrpc.METHOD_HANDLE_COMMAND)
          .withRequest(
            HandleCommandRequest()
              .withCommand(command)
              .withPriorState(stateWrapper.getState)
              .withPriorEventMeta(stateWrapper.getMeta)
          )
          .withHeader("header-1", "header-value-1")
          .willReturn(
            response(HandleCommandResponse().withEvent(Any.pack(event)))
          )
      )

      stubFor(
        unaryMethod(WriteSideHandlerServiceGrpc.METHOD_HANDLE_EVENT)
          .withRequest(
            HandleEventRequest()
              .withPriorState(stateWrapper.getState)
              .withEventMeta(stateWrapper.getMeta)
              .withEvent(Any.pack(event))
          )
          .willReturn(
            response(HandleEventResponse().withResultingState(resultingState))
          )
      )

      val writeHandlerServicetub: WriteSideHandlerServiceBlockingStub =
        new WriteSideHandlerServiceBlockingStub(serverChannel)

      // Let us create the sender of commands
      val commandSender: TestProbe[CommandReply] =
        createTestProbe[CommandReply]()

      val remoteCommandHandler: RemoteCommandHandler =
        RemoteCommandHandler(mainConfig.grpcConfig, writeHandlerServicetub)
      val remoteEventHandler: RemoteEventHandler = RemoteEventHandler(mainConfig.grpcConfig, writeHandlerServicetub)
      val shardIndex = 0
      val eventsAndStateProtosValidation: EventsAndStateProtosValidation =
        EventsAndStateProtosValidation(mainConfig.writeSideConfig)

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
        case CommandReply(reply, _) =>
          reply match {
            case Reply.Empty    => fail("unexpected message state")
            case Reply.State(_) => fail("unexpected message state")
            case Reply.Failure(failureResponse) =>
              failureResponse shouldBe FailureResponse().withCritical(
                "[ChiefOfState] received unknown state type: type.googleapis.com/chief_of_state.v1.Account"
              )
          }
        case _ => fail("unexpected message type")
      }

    }
  }

  ".getStateCommand" should {
    "return as expected" in {
      val aggregateId: String = UUID.randomUUID().toString
      val persistenceId: PersistenceId = PersistenceId("chiefofstate", aggregateId)
      val state: Account = Account().withAccountUuid(aggregateId)
      val stateWrapper: StateWrapper = StateWrapper()
        .withState(any.Any.pack(Empty.defaultInstance))
        .withMeta(
          MetaData.defaultInstance
            .withEntityId(getEntityId(persistenceId))
        )
      val command: Any = Any.pack(OpenAccount())
      val event: AccountOpened = AccountOpened()

      val resultingState = com.google.protobuf.any.Any.pack(state.withBalance(200))
      stubFor(
        unaryMethod(WriteSideHandlerServiceGrpc.METHOD_HANDLE_COMMAND)
          .withRequest(
            HandleCommandRequest()
              .withCommand(command)
              .withPriorState(stateWrapper.getState)
              .withPriorEventMeta(stateWrapper.getMeta)
          )
          .withHeader("header-1", "header-value-1")
          .willReturn(
            response(HandleCommandResponse().withEvent(Any.pack(event)))
          )
      )

      stubFor(
        unaryMethod(WriteSideHandlerServiceGrpc.METHOD_HANDLE_EVENT)
          .withRequest(
            HandleEventRequest()
              .withPriorState(stateWrapper.getState)
              .withEventMeta(stateWrapper.getMeta)
              .withEvent(Any.pack(event))
          )
          .willReturn(
            response(HandleEventResponse().withResultingState(resultingState))
          )
      )

      val writeHandlerServicetub: WriteSideHandlerServiceBlockingStub =
        new WriteSideHandlerServiceBlockingStub(serverChannel)
      // Let us create the sender of commands
      val commandSender: TestProbe[CommandReply] =
        createTestProbe[CommandReply]()

      val remoteCommandHandler: RemoteCommandHandler =
        RemoteCommandHandler(cosConfig.grpcConfig, writeHandlerServicetub)
      val remoteEventHandler: RemoteEventHandler = RemoteEventHandler(cosConfig.grpcConfig, writeHandlerServicetub)
      val shardIndex = 0
      val eventsAndStateProtosValidation: EventsAndStateProtosValidation =
        EventsAndStateProtosValidation(cosConfig.writeSideConfig)

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
        case CommandReply(reply, _) =>
          reply match {
            case Reply.Empty => fail("unexpected message state")
            case Reply.State(value: StateWrapper) =>
              val account: Account = value.getState.unpack[Account]
              account.accountUuid shouldBe aggregateId
              account.balance shouldBe 200
              value.getMeta.revisionNumber shouldBe 1
              value.getMeta.entityId shouldBe aggregateId
            case Reply.Failure(failureResponse: FailureResponse) => fail(s"unexpected message state $failureResponse")
          }
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
            case Reply.Empty => fail("unexpected message state")
            case Reply.State(value: StateWrapper) =>
              val account: Account = value.getState.unpack[Account]
              account.accountUuid shouldBe aggregateId
              account.balance shouldBe 200
              value.getMeta.revisionNumber shouldBe 1
              value.getMeta.entityId shouldBe aggregateId
            case Reply.Failure(failureResponse: FailureResponse) => fail(s"unexpected message state $failureResponse")
          }
        case _ => fail("unexpected message type")
      }
    }
    "return a failure when there is no entity as expected" in {
      val aggregateId: String = UUID.randomUUID().toString
      val persistenceId: PersistenceId = PersistenceId("chiefofstate", aggregateId)

      val writeHandlerServicetub: WriteSideHandlerServiceBlockingStub =
        new WriteSideHandlerServiceBlockingStub(serverChannel)
      // Let us create the sender of commands
      val commandSender: TestProbe[CommandReply] =
        createTestProbe[CommandReply]()

      val remoteCommandHandler: RemoteCommandHandler =
        RemoteCommandHandler(cosConfig.grpcConfig, writeHandlerServicetub)
      val remoteEventHandler: RemoteEventHandler = RemoteEventHandler(cosConfig.grpcConfig, writeHandlerServicetub)
      val shardIndex = 0
      val eventsAndStateProtosValidation: EventsAndStateProtosValidation =
        EventsAndStateProtosValidation(cosConfig.writeSideConfig)

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
            case Reply.Empty    => fail("unexpected message state")
            case Reply.State(_) => fail("unexpected message state")
            case Reply.Failure(failureResponse) =>
              failureResponse shouldBe FailureResponse().withNotFound(
                s"[ChiefOfState] entity: ${aggregateId} not found"
              )
          }
        case _ => fail("unexpected message type")
      }
    }
  }
}
