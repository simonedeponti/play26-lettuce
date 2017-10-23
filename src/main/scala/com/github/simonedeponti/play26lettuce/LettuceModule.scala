package com.github.simonedeponti.play26lettuce

import com.typesafe.config.ConfigException.Missing
import play.cache.NamedCacheImpl
import play.cache.{AsyncCacheApi => JavaAsyncCacheApi, SyncCacheApi => JavaSyncCacheApi}
import play.api.cache.{AsyncCacheApi, NamedCache, SyncCacheApi}
import play.api.{Configuration, Environment}
import play.api.inject._

import scala.util.{Failure, Success, Try}


class LettuceModule extends SimpleModule((environment: Environment, configuration: Configuration) => {

  import scala.collection.JavaConverters._

  val defaultCacheName = configuration.underlying.getString("play.cache.defaultCache")
  val bindCaches = Try(configuration.underlying.getStringList("play.cache.bindCaches")) match {
    case Success(v) => v.asScala
    case Failure(e) =>
      e match {
        case m: Missing => Seq()
        case e: Throwable => throw e
      }
  }

  // Creates a named cache qualifier
  def named(name: String): NamedCache = {
    new NamedCacheImpl(name)
  }

  // bind a cache with the given name
  def bindCache(name: String) = {
    val namedCache = named(name)
    Seq(
      bind[LettuceCacheApi].qualifiedWith(namedCache).to(new LettuceClientProvider(configuration, name)),
      bind[AsyncCacheApi].qualifiedWith(namedCache).to(new LettuceClientProvider(configuration, name)),
      bind[SyncCacheApi].qualifiedWith(namedCache).to(new SyncWrapperProvider(configuration, name)),
      bind[JavaAsyncCacheApi].qualifiedWith(namedCache).to(new JavaAsyncWrapperProvider(configuration, name)),
      bind[JavaSyncCacheApi].qualifiedWith(namedCache).to(new JavaSyncWrapperProvider(configuration, name))
    )
  }

  Seq(
    bind[LettuceCacheApi].to(new LettuceClientProvider(configuration, defaultCacheName)),
    bind[AsyncCacheApi].to(new LettuceClientProvider(configuration, defaultCacheName)),
    bind[SyncCacheApi].to(new SyncWrapperProvider(configuration, defaultCacheName)),
    bind[JavaAsyncCacheApi].to(new JavaAsyncWrapperProvider(configuration, defaultCacheName)),
    bind[JavaSyncCacheApi].to(new JavaSyncWrapperProvider(configuration, defaultCacheName))
  ) ++ bindCaches.flatMap(bindCache)
})