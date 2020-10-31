package com.namely.chiefofstate

import com.namely.chiefofstate.config.CosConfig

/**
 * Validates the events and states emitted by both the command and events handler
 * in case proto validation is enabled
 *
 * @param cosConfig the main configuration
 */
case class EventsAndStateProtosValidation(cosConfig: CosConfig) {

  private val isValidationEnabled: Boolean = cosConfig.writeSideConfig.enableProtoValidation
  private val validEventsProtos: Seq[String] = cosConfig.writeSideConfig.eventsProtos
  private val validStatesProtos: Seq[String] = cosConfig.writeSideConfig.statesProtos

  /**
   * validates an event proto message and return true when it is valid or false when it is not
   *
   * @param event the event to validate
   * @return true or false
   */
  def validateEvent(event: com.google.protobuf.any.Any): Boolean = {
    if (isValidationEnabled) {
      isValidationEnabled && !validStatesProtos.contains(Util.getProtoFullyQualifiedName(event))
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
      isValidationEnabled && !validEventsProtos.contains(Util.getProtoFullyQualifiedName(state))
    } else {
      true
    }
  }
}
