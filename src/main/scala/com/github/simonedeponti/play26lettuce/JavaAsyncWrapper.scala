package com.github.simonedeponti.play26lettuce

import java.util.concurrent.{Callable, CompletionStage}
import javax.inject.Inject

import akka.Done
import play.cache.{AsyncCacheApi, SyncCacheApi}

import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag


class JavaAsyncWrapper @Inject()(val acache: LettuceCacheApi)
                                (implicit val ec: ExecutionContext) extends AsyncCacheApi {

  override def sync(): SyncCacheApi = new JavaSyncWrapper(acache)(ec)

  override def get[T](key: String): CompletionStage[T] = {
    // NOTE: This is a bit weird and non-idiomatic but it's the only way it compiles
    //noinspection GetOrElseNull
    acache.get[T](key)(ClassTag.apply[T](getClass)).map(_.getOrElse(null).asInstanceOf[T]).toJava
  }

  override def set(key: String, value: scala.Any): CompletionStage[Done] = {
    acache.set(key, value).toJava
  }

  override def set(key: String, value: scala.Any, expiration: Int): CompletionStage[Done] = {
    acache.set(key, value, Duration(expiration, "seconds")).toJava
  }

  override def getOrElseUpdate[T](key: String, block: Callable[CompletionStage[T]]): CompletionStage[T] = {
    acache.getOrElseUpdate[T](key, Duration.Inf) {
      block.call().toScala
    } (ClassTag.apply[T](getClass)).toJava
  }

  override def getOrElseUpdate[T](key: String, block: Callable[CompletionStage[T]], expiration: Int): CompletionStage[T] = {
    acache.getOrElseUpdate[T](key, Duration(expiration, "seconds")) {
      block.call().toScala
    } (ClassTag.apply[T](getClass)).toJava
  }

  override def remove(key: String): CompletionStage[Done] = {
    acache.remove(key).toJava
  }

  override def removeAll(): CompletionStage[Done] = {
    acache.removeAll().toJava
  }
}