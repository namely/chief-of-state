/*
 * MIT License
 *
 * Copyright (c) 2020 Namely
 */

package com.namely.chiefofstate.helper

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ActorTestKitBase}
import akka.actor.testkit.typed.TestKitSettings
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.time.Span

class BaseActorSpec(testKit: ActorTestKit) extends ActorTestKitBase(testKit) with BaseSpec {

  /**
   * Config loaded from `application-test.conf` if that exists, otherwise
   * using default configuration from the lagompb.conf resources that ship with the Akka libraries.
   * The application.conf of your project is not used in this case.
   */
  def this() = this(ActorTestKit(ActorTestKitBase.testNameFromCallStack()))

  /**
   * Use a custom config for the actor system.
   */
  def this(config: String) =
    this(
      ActorTestKit(
        ActorTestKitBase.testNameFromCallStack(),
        ConfigFactory.parseString(config)
      )
    )

  /**
   * Use a custom config for the actor system.
   */
  def this(config: Config) =
    this(ActorTestKit(ActorTestKitBase.testNameFromCallStack(), config))

  /**
   * Use a custom config for the actor system, and a custom akka TestKitSettings.
   */
  def this(config: Config, settings: TestKitSettings) =
    this(
      ActorTestKit(ActorTestKitBase.testNameFromCallStack(), config, settings)
    )

  /**
   * `PatienceConfig` from akka test kit default timeout
   */
  implicit val patience: PatienceConfig =
    PatienceConfig(
      testKit.testKitSettings.DefaultTimeout.duration,
      Span(100, org.scalatest.time.Millis)
    )

  /**
   * Shuts down the ActorTestKit. If override be sure to call super.afterAll
   * or shut down the testkit explicitly with `testKit.shutdownTestKit()`.
   */
  override protected def afterAll(): Unit =
    testKit.shutdownTestKit()
}
