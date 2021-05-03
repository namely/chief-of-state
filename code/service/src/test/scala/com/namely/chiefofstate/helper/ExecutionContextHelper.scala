/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.helper

import scala.concurrent.ExecutionContext

trait ExecutionContextHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global
}
