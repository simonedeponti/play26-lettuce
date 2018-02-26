package com.github.simonedeponti.play26lettuce

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
    * @tparam T The trype of the value
    * @return A [[Future]] wrapping an [[Option]] of the object being returned
    */
  def javaGet[T](key: String): Future[Option[T]]

  /** A getOrElseUpdate method optimized for Java wrappers
    *
    * @param key The key to get or update
    * @param expiration The TTL of the key if update is performed
    * @param orElse A function computing the value to insert in cache if the key is missing
    * @tparam A The type of the value
    * @return A [[Future]] wrapping the result (unlike a get, there is always a result, either fetched or computed)
    */
  def javaGetOrElseUpdate[A](key: String, expiration: Duration)(orElse: => Future[A]): Future[A]
}
