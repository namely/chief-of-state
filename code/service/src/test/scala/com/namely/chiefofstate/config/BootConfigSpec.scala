/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.config

import com.namely.chiefofstate.helper.{BaseSpec, EnvironmentHelper}
import com.typesafe.config.Config

class BootConfigSpec extends BaseSpec {

  override def beforeEach(): Unit = {
    super.beforeEach()
    EnvironmentHelper.clearEnv()
  }

  ".getDeploymentMode" should {
    "return docker configs" in {
      val mode = BootConfig.getDeploymentMode(BootConfig.DEPLOYMENT_MODE_DOCKER.key)
      mode shouldBe BootConfig.DEPLOYMENT_MODE_DOCKER
    }

    "return k8s configs" in {
      val mode = BootConfig.getDeploymentMode(BootConfig.DEPLOYMENT_MODE_K8S.key)
      mode shouldBe BootConfig.DEPLOYMENT_MODE_K8S
    }

    "error on unknown config" in {
      val actual: IllegalArgumentException = intercept[IllegalArgumentException] {
        BootConfig.getDeploymentMode("not a mode")
      }

      actual.getMessage().contains("not a mode") shouldBe true
    }

    "read the env var" in {
      EnvironmentHelper.setEnv(BootConfig.DEPLOYMENT_MODE, BootConfig.DEPLOYMENT_MODE_K8S.key)
      BootConfig.getDeploymentMode() shouldBe BootConfig.DEPLOYMENT_MODE_K8S
    }
  }

  ".get" should {
    "run e2e" in {
      EnvironmentHelper.setEnv(BootConfig.DEPLOYMENT_MODE, BootConfig.DEPLOYMENT_MODE_DOCKER.key)
      val config: Config = BootConfig.get()
      config.getString("deployment-mode") shouldBe "docker"
    }

    "run e2e with journal legacy schema" in {
      EnvironmentHelper.setEnv(BootConfig.DEPLOYMENT_MODE, BootConfig.DEPLOYMENT_MODE_DOCKER.key)
      EnvironmentHelper.setEnv(BootConfig.COS_JOURNAL_USE_LEGACY_SCHEMA, "true")

      val config: Config = BootConfig.get()
      config.getString("deployment-mode") shouldBe "docker"
      config.getString("jdbc-journal.dao") shouldBe "akka.persistence.jdbc.journal.dao.legacy.ByteArrayJournalDao"
      config.getString(
        "jdbc-snapshot-store.dao"
      ) shouldBe "akka.persistence.jdbc.snapshot.dao.legacy.ByteArraySnapshotDao"
      config.getString(
        "jdbc-read-journal.dao"
      ) shouldBe "akka.persistence.jdbc.query.dao.legacy.ByteArrayReadJournalDao"
    }
  }
}
