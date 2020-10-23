package com.namely.chiefofstate

import org.scalamock.scalatest.MockFactory
import scala.util.Try
import io.superflat.lagompb.encryption.EncryptionAdapter
import akka.persistence.typed.PersistenceId
import com.namely.chiefofstate.test.helpers.TestSpec
import io.superflat.lagompb.{CommandHandler, EventHandler}

class AggregateSpec extends TestSpec with MockFactory {
  ".create" should {
    "return EventSourcedBehavior with adapters" in {
      val cmdHandler: CommandHandler = mock[CommandHandler]
      val eventHandler: EventHandler = mock[EventHandler]
      val encryptionAdapter: EncryptionAdapter = mock[EncryptionAdapter]
      val agg = new Aggregate(null, null, cmdHandler, eventHandler, encryptionAdapter)
      val persistenceId: PersistenceId = PersistenceId("typeHint", "entityId")
      // TODO: find a real way to test this
      // unfortunately, Akka made the implementation case class private,
      // so there is no way to observe the eventAdatper and snapshotAdapter
      Try(agg.create(persistenceId)).isSuccess shouldBe (true)
    }
  }
}
