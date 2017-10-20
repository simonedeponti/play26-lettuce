package com.github.simonedeponti.play26lettuce

import akka.Done
import org.specs2.mutable._
import org.specs2.concurrent.ExecutionEnv
import play.api.cache.AsyncCacheApi
import play.cache.NamedCacheImpl

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}


class LettuceSpec extends Specification {

  sequential

  private val redisURL = Try(sys.env("REDIS_URL")) match {
    case Success(v) => v
    case Failure(e) => e match {
      case nse: NoSuchElementException => "redis://localhost/0"
      case e: Throwable => throw e
    }
  }

  private val environment = play.api.Environment.simple()

  private val configurationMap: Map[String, Object] = Map(
    "play.modules.enabled" -> List(
      "play.api.i18n.I18nModule",
      "play.api.mvc.CookiesModule",
      "com.github.simonedeponti.play26lettuce.LettuceModule",
      "play.api.inject.BuiltinModule"
    ).asJava,
    "play.allowGlobalApplication" -> "false",
    "play.cache.defaultCache" -> "default",
    "play.cache.bindCaches" -> List("secondary").asJava,
    "lettuce.default.url" -> redisURL,
    "lettuce.secondary.url" -> redisURL
  )
  private val configuration = play.api.Configuration.from(
    configurationMap
  )

  private val modules = play.api.inject.Modules.locate(environment, configuration)

  "play26-lettuce" should {
    "provide LettuceModule" in {
      modules.find { module => module.isInstanceOf[LettuceModule] }.get.asInstanceOf[LettuceModule] must beAnInstanceOf[LettuceModule]
    }
  }

  "Module" should {
    "provide bindings" in {
      val lettuceModule = modules.find { module => module.isInstanceOf[LettuceModule] }.get.asInstanceOf[LettuceModule]

      val bindings = lettuceModule.bindings(environment, configuration)

      bindings.size mustNotEqual 0
    }
  }

  "Injector" should {

    def app = play.test.Helpers.fakeApplication(
      configurationMap.asJava
    )

    def injector = app.injector

    "provide lettuce clients" in {
      val lettuceClient = injector.instanceOf(play.api.inject.BindingKey(classOf[LettuceCacheApi]))

      lettuceClient must beAnInstanceOf[LettuceClient]
    }

    "provide a CacheApi implementation backed by lettuce" in {
      val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[AsyncCacheApi]))

      cacheApi must beAnInstanceOf[LettuceCacheApi]
      cacheApi.asInstanceOf[LettuceCacheApi].name must equalTo ("default")
    }

    "provide a named CacheApi implementation backed by lettuce" in {
      val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[AsyncCacheApi]).qualifiedWith(new NamedCacheImpl("secondary")))

      cacheApi must beAnInstanceOf[LettuceCacheApi]
      cacheApi.asInstanceOf[LettuceCacheApi].name must equalTo ("secondary")
    }
  }

  "CacheApi" should {

    def app = play.test.Helpers.fakeApplication(
      configurationMap.asJava
    )

    def injector = app.injector

    "set correctly with infinite expire" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[AsyncCacheApi]))
        val result_inf: Future[Done] = cacheApi.set("foo", 1)

        result_inf must beAnInstanceOf[Done].await
      }
    }

    "set correctly with infinite expire" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[AsyncCacheApi]))
        val result_fin: Future[Done] = cacheApi.set("bar", 1, Duration(1, "seconds"))

        result_fin must beAnInstanceOf[Done].await
      }
    }

    "get some if present" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[AsyncCacheApi]))
        val result_ok: Future[Option[Int]] = cacheApi.set("baz", 1).flatMap(
          _ => cacheApi.get("baz")
        )

        result_ok must beSome(1).await
      }
    }

    "get none if not present" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[AsyncCacheApi]))
        val result_ko: Future[Option[Int]] = cacheApi.get("taz")

        result_ko must beNone.await
      }
    }
  }

}
