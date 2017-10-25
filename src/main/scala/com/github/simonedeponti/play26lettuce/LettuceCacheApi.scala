package com.github.simonedeponti.play26lettuce

import play.api.cache.AsyncCacheApi

import scala.concurrent.Future
import scala.concurrent.duration.Duration


trait LettuceCacheApi extends AsyncCacheApi {
  val name: String

  def javaGet[T](key: String): Future[Option[T]]

  def javaGetOrElseUpdate[A](key: String, expiration: Duration)(orElse: => Future[A]): Future[A]
}
