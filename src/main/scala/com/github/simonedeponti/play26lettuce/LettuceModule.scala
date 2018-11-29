package com.github.simonedeponti.play26lettuce

import com.typesafe.config.ConfigException.Missing
import play.cache.NamedCacheImpl
import play.cache.{AsyncCacheApi => JavaAsyncCacheApi, SyncCacheApi => JavaSyncCacheApi}
import play.api.cache.{AsyncCacheApi, NamedCache, SyncCacheApi}
import play.api.{Configuration, Environment}
import play.api.inject._

import scala.util.{Failure, Success, Try}


/** The dependency-injection module for the cache adapter **/
class LettuceModule extends SimpleModule((_: Environment, configuration: Configuration) => {

  import scala.collection.JavaConverters._

  val defaultCacheName = Try(configuration.underlying.getString("play.cache.defaultCache")).toOption
  val bindCaches = Try(configuration.underlying.getStringList("play.cache.bindCaches")) match {
    case Success(v) => v.asScala
    case Failure(e) =>
      e match {
        case _: Missing => Seq()
        case _: Throwable => throw e
      }
  }

  if(defaultCacheName.isDefined) {
    // Checks that two caches don't have the same URL by mistake
    val cacheURLs: Map[String, String] = Map("default" -> configuration.underlying.getString("lettuce.default.url")) ++ (
      for (cacheName <- bindCaches) yield {
        cacheName -> configuration.underlying.getString(s"lettuce.$cacheName.url")
      }).toMap
    cacheURLs.toSeq.groupBy[String](_._2).foreach({
      case (url: String, values: Seq[(String, String)]) =>
        if(values.lengthCompare(1) > 0) {
          val sameURLs: Seq[String] = values.map(i => s"lettuce.${i._1}.url")
          throw configuration.reportError(
            sameURLs.head,
            s"""has the same URL ($url) as ${sameURLs.tail.mkString(" and ")}.
               | This will cause a key collision so please use a different Redis database
               | (the number at the end) for every cache""".stripMargin
          )
        }
    })
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

  defaultCacheName.map(defaultCacheName => Seq(
    bind[LettuceCacheApi].to(new LettuceClientProvider(configuration, defaultCacheName)),
    bind[AsyncCacheApi].to(new LettuceClientProvider(configuration, defaultCacheName)),
    bind[SyncCacheApi].to(new SyncWrapperProvider(configuration, defaultCacheName)),
    bind[JavaAsyncCacheApi].to(new JavaAsyncWrapperProvider(configuration, defaultCacheName)),
    bind[JavaSyncCacheApi].to(new JavaSyncWrapperProvider(configuration, defaultCacheName))
  )).getOrElse(Nil) ++ bindCaches.flatMap(bindCache)
})
