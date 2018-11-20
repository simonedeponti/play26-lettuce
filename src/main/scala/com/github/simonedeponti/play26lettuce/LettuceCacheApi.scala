package com.github.simonedeponti.play26lettuce

import akka.Done
import play.api.cache.AsyncCacheApi

import scala.concurrent.Future
import scala.concurrent.duration.Duration


/** An extension of Play's [[play.api.cache.AsyncCacheApi]].
  *
  * It provides some extra features that simplify dealing with Redis
  */
trait LettuceCacheApi extends AsyncCacheApi {
  /** The cache name is mandatory, "default" is used for the anonymous cache **/
  val name: String

  /** A get method that is optimized for Java wrappers.
    *
    * @param key The key to get
    * @tparam T The type of the value
    * @return A [[scala.concurrent.Future]] wrapping an [[scala.Option]] of the object being returned
    */
  def javaGet[T](key: String): Future[Option[T]]

  /** A getOrElseUpdate method optimized for Java wrappers
    *
    * @param key The key to get or update
    * @param expiration The TTL of the key if update is performed
    * @param orElse A function computing the value to insert in cache if the key is missing
    * @tparam A The type of the value
    * @return A [[scala.concurrent.Future]] wrapping the result (unlike a get, there is always a result, either fetched or computed)
    */
  def javaGetOrElseUpdate[A](key: String, expiration: Duration)(orElse: => Future[A]): Future[A]

  /** A multi-key get method
   *
   * @param keys A sequence of keys to get
   * @tparam T The type of the value
   * @return A [[scala.concurrent.Future]] wrapping a [[scala.collection.Seq]] of [[scala.Option]]s of the object being returned, one for each requested key
   */
  def getAll[T <: AnyRef](keys: Seq[String]): Future[Seq[Option[T]]]

  /** A multi-key remove method
   *
   * @param keys A sequence to keys to remove
   * @return A [[scala.concurrent.Future]] that indicates when the keys have been removed
   */
  def remove(keys: Seq[String]): Future[Done]

  /** A multi-key set method
   *
   * Sets the keys to their respective values, with no expiry. Replaces existing values with new values.
   *
   * @param keyValues key-value pairs
   * @return A [[scala.concurrent.Future]] that indicates when the keys have been set
   */
  def setAll(keyValues: Map[String, AnyRef]): Future[Done]
}
