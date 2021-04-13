/*
 * Copyright 2020 Namely Inc.
 *
 * SPDX-License-Identifier: MIT
 */

package com.namely.chiefofstate.serialization

import akka.actor.typed.ActorRef
import scalapb.GeneratedMessage

// defines a trait that routes to the custom serializer
sealed trait ScalaMessage

/**
 * wraps a generated message and an actor ref that the serializer
 * can convert to/from proto
 *
 * @param message a generated message
 * @param actorRef an actor ref
 */
case class MessageWithActorRef(message: GeneratedMessage, actorRef: ActorRef[GeneratedMessage]) extends ScalaMessage
