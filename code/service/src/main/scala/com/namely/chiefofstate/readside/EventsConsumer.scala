package com.namely.chiefofstate.readside

import akka.projection.eventsourced.EventEnvelope
import akka.projection.slick.SlickHandler
import akka.Done
import com.namely.chiefofstate.Util
import com.namely.protobuf.chiefofstate.v1.persistence.EventWrapper
import io.superflat.lagompb.encryption.{DecryptPermanentFailure, EncryptionAdapter}
import io.superflat.lagompb.protobuf.v1.core.{EventWrapper => LagompbEventWrapper}
import org.slf4j.{Logger, LoggerFactory}
import slick.dbio.{DBIO, DBIOAction}

import scala.util.{Failure, Success, Try}

/**
 * Consumes all events in the journal based upon an event tag
 *
 * @param eventTag the event tag
 * @param encryptionAdapter the encryption adapter
 * @param readSideEventProcessor the event processor of the consumed events
 */
class EventsConsumer(eventTag: String, encryptionAdapter: EncryptionAdapter, readSideEventProcessor: EventsProcessor)
    extends SlickHandler[EventEnvelope[EventWrapper]] {

  val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * polls events from the journal and hands it over to the event to the processor based upon the
   * consumed tag
   *
   * @param envelope the event envelope
   */
  override def process(envelope: EventEnvelope[EventWrapper]): DBIO[Done] = {
    val cosEvent = envelope.event
    val lagompbEventWrapper: LagompbEventWrapper =
      LagompbEventWrapper()
        .withEvent(cosEvent.getEvent)
        .withMeta(Util.toLagompbMetaData(cosEvent.getMeta))
        .withResultingState(cosEvent.getResultingState)

    // decrypt the event/state as needed
    encryptionAdapter
      .decryptEventWrapper(lagompbEventWrapper)
      .map({
        case LagompbEventWrapper(Some(event), Some(resultingState), Some(meta), _) =>
          readSideEventProcessor.process(event, eventTag, resultingState, Util.toCosMetaData(meta))
        case _ =>
          DBIO.failed(
            new RuntimeException(
              s"[ChiefOfState] unknown event received ${envelope.event.getClass.getName}"
            )
          )
      })
      .recoverWith({
        case DecryptPermanentFailure(reason) =>
          log.debug(s"skipping offset with reason, $reason")
          Try(DBIOAction.successful(Done))

        case throwable: Throwable =>
          log.error("failed to handle event", throwable)
          Try(DBIO.failed(throwable))
      }) match {
      case Success(value)     => value
      case Failure(exception) => throw exception
    }
  }
}
