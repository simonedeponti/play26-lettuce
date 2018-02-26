package com.github.simonedeponti.play26lettuce

import javax.inject.Inject

import play.api.Configuration
import play.api.cache.SyncCacheApi

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.ClassTag


class SyncWrapper @Inject()(val acache: LettuceCacheApi, val configuration: Configuration)
                           (implicit val ec: ExecutionContext) extends SyncCacheApi with TimeoutConfigurable {

  override def get[T](key: String)(implicit ctag: ClassTag[T]): Option[T] = {
    Await.result(
      acache.get[T](key)(ctag),
      timeout
    )
  }

  override def set(key: String, value: Any, expiration: Duration): Unit = {
    Await.result(
      acache.set(key, value, expiration),
      timeout
    )
  }

  override def getOrElseUpdate[A](key: String, expiration: Duration)(orElse: => A)(implicit ctag: ClassTag[A]): A = {
    Await.result(
      acache.getOrElseUpdate(key, expiration) {
        Future {
          orElse
        }
      } (ctag),
      timeout
    )
  }

  override def remove(key: String): Unit = {
    Await.result(
      acache.remove(key),
      timeout
    )
  }
}
