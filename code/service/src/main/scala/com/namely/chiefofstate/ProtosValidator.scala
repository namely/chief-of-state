package com.namely.chiefofstate

import com.namely.chiefofstate.config.WriteSideConfig

/**
 * Validates the events and states emitted by both the command and events handler
 * in case proto validation is enabled
 *
 * @param writeSideConfig the write side configuration
 */
case class ProtosValidator(writeSideConfig: WriteSideConfig) {

  private val isValidationEnabled: Boolean = writeSideConfig.enableProtoValidation
  private val validEventsProtos: Seq[String] = writeSideConfig.eventsProtos
  private val validStatesProtos: Seq[String] = writeSideConfig.statesProtos

  /**
   * validates an event proto message and return true when it is valid or false when it is not
   *
   * @param event the event to validate
   * @return true or false
   */
  def validateEvent(event: com.google.protobuf.any.Any): Boolean = {
    if (isValidationEnabled) {
      isValidationEnabled && validEventsProtos.contains(Util.getProtoFullyQualifiedName(event))
    } else {
      true
    }
  }

  /**
   * validates an state proto message and return true when it is valid or false when it is not
   *
   * @param state  the state to validate
   * @return true or false
   */
  def validateState(state: com.google.protobuf.any.Any): Boolean = {
    if (isValidationEnabled) {
      isValidationEnabled && validStatesProtos.contains(Util.getProtoFullyQualifiedName(state))
    } else {
      true
    }
  }

  /**
   * helper to require known event types
   *
   * @param event an event as an Any
   */
  def requireValidEvent(event: com.google.protobuf.any.Any): Unit = {
    require(validateEvent(event), s"invalid event: ${event.typeUrl}")
  }

  /**
   * helper to require known state types
   *
   * @param state a state as an Any
   */
  def requireValidState(state: com.google.protobuf.any.Any): Unit = {
    require(validateState(state), s"invalid state: ${state.typeUrl}")
  }
}
