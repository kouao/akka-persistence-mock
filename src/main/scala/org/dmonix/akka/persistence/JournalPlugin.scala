/**
 *  Copyright 2015 Peter Nerg
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.dmonix.akka.persistence

import scala.collection.immutable.Seq
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try
import akka.persistence.{ AtomicWrite, PersistentRepr }
import akka.persistence.journal.{ AsyncRecovery, AsyncWriteJournal }
import scala.collection.mutable.HashMap
import akka.actor.ActorLogging
import scala.collection.mutable.MutableList

case class PersistedJournal(sequenceNr: Long, msg: Any)

class JournalStash {

  val journals = new MutableList[PersistedJournal]()

  def add(journal: PersistedJournal) {
    journals.+=(journal)
  }

  def getOrdered = journals.sortWith((l, r) => l.sequenceNr > r.sequenceNr)

  //    def filter() {
  //      
  //    }
}

class JournalStorage {
  val stashes = HashMap[String, JournalStash]()

  def add(persistenceId: String, journal: PersistedJournal) = synchronized {
    stashes.get(persistenceId) match {
      case Some(stash) => stash.add(journal)
      case None => {
        val stash = new JournalStash
        stash.add(journal)
        stashes.put(persistenceId, stash)
      }
    }

  }

  def get(persistenceId: String) = synchronized { stashes.get(persistenceId) }
}

/**
 * @author Peter Nerg
 */
class JournalPlugin extends AsyncWriteJournal with AsyncRecovery with ActorLogging {

  implicit val ec = ExecutionContext.global
  val storage = new JournalStorage

  def asyncWriteMessages(messages: Seq[AtomicWrite]): Future[Seq[Try[Unit]]] = {
    Future {
      messages.foreach(m => m.payload.foreach { p =>
        log.debug("Persist event [" + p.persistenceId + "] [" + p.sequenceNr + "] [" + p.payload + "]")
        storage.add(p.persistenceId, PersistedJournal(p.sequenceNr, p.payload))
      })
      List()
    }
  }

  def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = {
    Future {
    }
  }

  def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long,
                          max: Long)(recoveryCallback: PersistentRepr ⇒ Unit): Future[Unit] = {

    Future {
    }
  }

  def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    log.debug("Read highest seqNr [{}] [{}]", persistenceId, fromSequenceNr)
    Future {
      storage.get(persistenceId).flatMap(stash => {
        stash.getOrdered.map(j => j.sequenceNr).filter(s => s >= fromSequenceNr).headOption
      }).getOrElse(0)
    }
  }
}