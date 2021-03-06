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

import scala.collection.mutable.HashMap
import scala.collection.mutable.MutableList

/**
 * Utility/helper methods.
 */
private[persistence] object Utils {
  /**
   * Simple helper method to determine of a value is the expected range.
   * @param value The value to check
   * @param min The expected minimum (inclusive) value
   * @param min The expected maximum (inclusive) value
   */
  def inRange(value: Long, min: Long, max: Long) = value >= min && value <= max
}

/**
 * Trait for marking persistent data types
 */
private[persistence] trait PersistedState

/**
 * A data stash containing a id -> data mapping. <br>
 * Used both to store journal/transactions as well as snapshots for a single actor instance. <br>
 * The order of the data is kept by always appending the journal/snapshot to the end of the list. <br>
 * A single actor can only persist one state at a time so this approach is safe.
 * @author Peter Nerg
 */
private[persistence] class Stash[S <: PersistedState] {
  private var stashedState = List[(Long, S)]()

  def add(id: Long, state: S): Unit = stashedState = stashedState :+ (id, state)

  def delete(id: Long): Unit = stashedState = stashedState.filterNot(_._1 == id)
  
  def select(criteria: S => Boolean) = stashedState.filter(t => criteria(t._2)).map(_._2)
  
  def ids() = stashedState.map(_._1)
}

/**
 * Storage for all data stashes. <br>
 * Contains a mapping of actor (persistenceId) -> data stash.
 * @author Peter Nerg
 */
private[persistence] class Storage[T <: PersistedState] {
  /** stores persistenceId -> Snapshot*/
  private val stashes = HashMap[String, Stash[T]]()

  def add(persistenceId: String)(id: Long, snap: T) {
    stashes.get(persistenceId) match {
      case Some(stash) => stash.add(id, snap)
      case None => {
        val stash = new Stash[T]
        stash.add(id, snap)
        stashes.put(persistenceId, stash)
      }
    }
  }

  def get(persistenceId: String) = stashes.get(persistenceId)
}
