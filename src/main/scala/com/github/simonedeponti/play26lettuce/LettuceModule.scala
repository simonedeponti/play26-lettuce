package com.github.simonedeponti.play26lettuce

import play.cache.NamedCacheImpl
import play.api.cache.{AsyncCacheApi, NamedCache}
import play.api.{Configuration, Environment}
import play.api.inject._


class LettuceModule extends SimpleModule((environment: Environment, configuration: Configuration) => {

  import scala.collection.JavaConverters._

  val defaultCacheName = configuration.underlying.getString("play.cache.defaultCache")
  val bindCaches = configuration.underlying.getStringList("play.cache.bindCaches").asScala

  // Creates a named cache qualifier
  def named(name: String): NamedCache = {
    new NamedCacheImpl(name)
  }

  // bind a cache with the given name
  def bindCache(name: String) = {
    val namedCache = named(name)
    Seq(
      bind[LettuceCacheApi].qualifiedWith(namedCache).to(new LettuceClientProvider(configuration, name)),
      bind[AsyncCacheApi].qualifiedWith(namedCache).to(new LettuceClientProvider(configuration, name))
    )
  }

  Seq(
    bind[LettuceCacheApi].to(new LettuceClientProvider(configuration, defaultCacheName)),
    bind[AsyncCacheApi].to(new LettuceClientProvider(configuration, defaultCacheName))
  ) ++ bindCaches.flatMap(bindCache)
})