package com.github.simonedeponti.play26lettuce

import java.time.Instant
import java.util.Date
import java.util.concurrent.{Callable, CompletionStage}

import akka.Done
import org.specs2.mutable._
import org.specs2.concurrent.ExecutionEnv
import play.api.cache.{AsyncCacheApi, SyncCacheApi}
import play.cache.{AsyncCacheApi => JavaAsyncCacheApi, SyncCacheApi => JavaSyncCacheApi}
import play.cache.NamedCacheImpl

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}
import scala.compat.java8.FutureConverters._


class LettuceSpec extends Specification {

  sequential

  private val redisURL = Try(sys.env("REDIS_URL")) match {
    case Success(v) => v
    case Failure(e) => e match {
      case _: NoSuchElementException => "redis://localhost/0"
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
  private val emptyConfiguration = play.api.Configuration.empty

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

    "provide no bindings with empty configuration" in {
      val lettuceModule = modules.find { module => module.isInstanceOf[LettuceModule] }.get.asInstanceOf[LettuceModule]

      val bindings = lettuceModule.bindings(environment, emptyConfiguration)

      bindings.size mustEqual 0
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

    "provide a AsyncCacheApi implementation backed by lettuce" in {
      val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[AsyncCacheApi]))

      cacheApi must beAnInstanceOf[LettuceCacheApi]
      cacheApi.asInstanceOf[LettuceCacheApi].name must equalTo ("default")
    }

    "provide a named AsyncCacheApi implementation backed by lettuce" in {
      val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[AsyncCacheApi]).qualifiedWith(new NamedCacheImpl("secondary")))

      cacheApi must beAnInstanceOf[LettuceCacheApi]
      cacheApi.asInstanceOf[LettuceCacheApi].name must equalTo ("secondary")
    }

    "provide a SyncCacheApi implementation backed by lettuce" in {
      val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[SyncCacheApi]))

      cacheApi must beAnInstanceOf[SyncWrapper]
      cacheApi.asInstanceOf[SyncWrapper].acache.name must equalTo ("default")
    }

    "provide a named SyncCacheApi implementation backed by lettuce" in {
      val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[SyncCacheApi]).qualifiedWith(new NamedCacheImpl("secondary")))

      cacheApi must beAnInstanceOf[SyncWrapper]
      cacheApi.asInstanceOf[SyncWrapper].acache.name must equalTo ("secondary")
    }
  }

  "LettuceCacheApi" should {

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

    "set correctly with finite expire" in {
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
          _ => cacheApi.get[Int]("baz")
        )

        result_ok must beSome(1).await
      }
    }

    "get multiple some if present" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[LettuceCacheApi]))

        val result: Future[Seq[Option[Integer]]] = for {
          _ <- cacheApi.set("qux", 1)
          _ <- cacheApi.set("quux", 2)
          get <- cacheApi.getAll[Integer](Seq("qux", "quux", "not-set"))
        } yield get

        result must beEqualTo(Seq(Some(1), Some(2), None)).await
      }
    }

    "set multiple" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[LettuceCacheApi]))

        val result: Future[(Option[Int], Option[Int])] = for {
          _ <- cacheApi.setAll(Map("blah" -> Int.box(1), "blaah" -> Int.box(2)))
          get1 <- cacheApi.get[Int]("blah")
          get2 <- cacheApi.get[Int]("blaah")
        } yield (get1, get2)

        result must beEqualTo((Some(1), Some(2))).await
      }
    }

    "remove multiple" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[LettuceCacheApi]))

        val result: Future[(Option[Int], Option[Int], Option[Int])] = for {
          _ <- cacheApi.setAll(Map("da" -> Int.box(1), "daa" -> Int.box(2), "daaa" -> Int.box(3)))
          _ <- cacheApi.remove(Seq("daa", "daaa"))
          get1 <- cacheApi.get[Int]("da")
          get2 <- cacheApi.get[Int]("daa")
          get3 <- cacheApi.get[Int]("daaa")
        } yield (get1, get2, get3)

        result must beEqualTo((Some(1), None, None)).await
      }
    }

    "get none if not present" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[AsyncCacheApi]))
        val result_ko: Future[Option[Int]] = cacheApi.get[Int]("taz")

        result_ko must beNone.await
      }
    }

    "get or else update" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[AsyncCacheApi]))
        val result_ok: Future[Int] = cacheApi.getOrElseUpdate[Int]("paz", Duration(10, "seconds")) {
          Future {
            1
          }
        }

        result_ok must beEqualTo(1).await

        val result_eq: Future[Int] = cacheApi.getOrElseUpdate[Int]("paz", Duration(10, "seconds")) {
          Future {
            2
          }
        }

        result_eq must beEqualTo(1).await
      }
    }

    "serialize correctly a scala class" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[AsyncCacheApi]))
        val testItem = TestItem("cat", 3)
        val result_ok: Future[Option[TestItem]] = cacheApi.set("cat", testItem).flatMap(
          _ => cacheApi.get[TestItem]("cat")
        )
        result_ok must beSome(testItem).await

        cacheApi.remove("cat") must beAnInstanceOf[Done].await
      }
    }

    "serialize correctly a void string" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[AsyncCacheApi]))
        val result_ok: Future[Option[String]] = cacheApi.set("void", "").flatMap(
          _ => cacheApi.get[String]("void")
        )
        result_ok must beSome("").await

        cacheApi.remove("void") must beAnInstanceOf[Done].await
      }
    }

    "remove deletes it" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[AsyncCacheApi]))

        val result_ok: Future[Option[Int]] = cacheApi.get[Int]("foo")
        result_ok must beSome(1).await

        val result_fin: Future[Done] = cacheApi.remove("foo")
        result_fin must beAnInstanceOf[Done].await

        val result_ko: Future[Option[Int]] = cacheApi.get[Int]("foo")
        result_ko must beNone.await
      }
    }

    "removeAll should not explode" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[AsyncCacheApi]))

        val result_fin: Future[Done] = cacheApi.removeAll()
        result_fin must beAnInstanceOf[Done].await
      }
    }

    "removeAll should remove a lot of keys successfully" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[LettuceCacheApi]))

        val result = for {
          _ <- cacheApi.set("foo1", "bar")
          _ <- cacheApi.set("foo2", "bar")
          _ <- cacheApi.set("foo3", "bar")
          _ <- cacheApi.set("foo4", "bar")
          _ <- cacheApi.set("foo5", "bar")
          _ <- cacheApi.set("foo6", "bar")
          _ <- cacheApi.set("foo7", "bar")
          _ <- cacheApi.set("foo8", "bar")
          _ <- cacheApi.set("foo9", "bar")
          _ <- cacheApi.set("foo10", "bar")
          _ <- cacheApi.set("foo11", "bar")
          _ <- cacheApi.set("foo12", "bar")
          removed <- cacheApi.removeAll()
        } yield removed

        result must beAnInstanceOf[Done].await
      }
    }
  }

  "SyncCacheApi" should {

    def app = play.test.Helpers.fakeApplication(
      configurationMap.asJava
    )

    def injector = app.injector

    "set and get correctly" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[SyncCacheApi]))
        cacheApi.set("foo", 1)
        val result: Option[Int] = cacheApi.get[Int]("foo")

        result must beSome(1)
      }
    }

    "get none if not present" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[SyncCacheApi]))
        val result_ko: Option[Int] = cacheApi.get[Int]("taz")

        result_ko must beNone
      }
    }

    "get or else update" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[SyncCacheApi]))
        val result_ok: Int = cacheApi.getOrElseUpdate[Int]("paz", Duration(10, "seconds")) {
          1
        }

        result_ok must beEqualTo(1)

        val result_eq: Int = cacheApi.getOrElseUpdate[Int]("paz", Duration(10, "seconds")) {
          2
        }

        result_eq must beEqualTo(1)
      }
    }

    "remove deletes it" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[SyncCacheApi]))

        val result_ok: Option[Int] = cacheApi.get[Int]("foo")
        result_ok must beSome(1)

        cacheApi.remove("foo")

        val result_ko: Option[Int] = cacheApi.get[Int]("foo")
        result_ko must beNone
      }
    }
  }

  "Java AsyncCacheApi" should {

    def app = play.test.Helpers.fakeApplication(
      configurationMap.asJava
    )

    def injector = app.injector

    "set correctly with infinite expire" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[JavaAsyncCacheApi]))
        val result_inf: Future[Done] = cacheApi.set("foo", new Integer(1)).toScala

        result_inf must beAnInstanceOf[Done].await
      }
    }

    "set correctly with finite expire" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[JavaAsyncCacheApi]))
        val result_fin: Future[Done] = cacheApi.set("bar", new Integer(1), 1).toScala

        result_fin must beAnInstanceOf[Done].await
      }
    }

    "get some if present" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[JavaAsyncCacheApi]))
        val result_ok: Future[Integer] = cacheApi.set("baz", new Integer(1)).toScala.flatMap(
          _ => cacheApi.get[Integer]("baz").toScala
        )

        result_ok must beEqualTo(new Integer(1)).await
      }
    }

    "get null if not present" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[JavaAsyncCacheApi]))
        val result_ko: Future[Integer] = cacheApi.get[Integer]("taz").toScala

        result_ko must beNull[Integer].await
      }
    }

    "get or else update" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[JavaAsyncCacheApi]))
        val orElse1 = new Callable[CompletionStage[Integer]] {
          def call(): CompletionStage[Integer] = Future { new Integer(1) }.toJava
        }
        val result_ok: Future[Integer] = cacheApi.getOrElseUpdate[Integer]("paz", orElse1, 10).toScala

        result_ok must beEqualTo(new Integer(1)).await

        val orElse2 = new Callable[CompletionStage[Integer]] {
          def call(): CompletionStage[Integer] = Future { new Integer(2) }.toJava
        }
        val result_eq: Future[Integer] = cacheApi.getOrElseUpdate[Integer]("paz", orElse2, 10).toScala

        result_eq must beEqualTo(new Integer(1)).await
      }
    }

    "serialize correctly a scala class" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[JavaAsyncCacheApi]))
        val testItem = TestItem("cat", 3)
        val result_ok: Future[TestItem] = cacheApi.set("cat", testItem).toScala.flatMap(
          _ => cacheApi.get[TestItem]("cat").toScala
        )
        result_ok must beEqualTo(testItem).await

        cacheApi.remove("cat").toScala must beAnInstanceOf[Done].await
      }
    }

    "serialize correctly a void string" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[JavaAsyncCacheApi]))
        val result_ok: Future[String] = cacheApi.set("void", "").toScala.flatMap(
          _ => cacheApi.get[String]("void").toScala
        )
        result_ok must beEqualTo("").await

        cacheApi.remove("void").toScala must beAnInstanceOf[Done].await
      }
    }

    "remove deletes it" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[JavaAsyncCacheApi]))

        val result_ok: Future[Integer] = cacheApi.get[Integer]("foo").toScala
        result_ok must beEqualTo(new Integer(1)).await

        val result_fin: Future[Done] = cacheApi.remove("foo").toScala
        result_fin must beAnInstanceOf[Done].await

        val result_ko: Future[Integer] = cacheApi.get[Integer]("foo").toScala
        result_ko must beNull[Integer].await
      }
    }

    "removeAll should not explode" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[JavaAsyncCacheApi]))

        val result_fin: Future[Done] = cacheApi.removeAll().toScala
        result_fin must beAnInstanceOf[Done].await
      }
    }
  }

  "Java SyncCacheApi" should {

    def app = play.test.Helpers.fakeApplication(
      configurationMap.asJava
    )

    def injector = app.injector

    "set and get correctly" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[JavaSyncCacheApi]))
        cacheApi.set("foo", new Integer(1))
        val result: Integer = cacheApi.get[Integer]("foo")

        result must beEqualTo(new Integer(1))
      }
    }

    "get null if not present" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[JavaSyncCacheApi]))
        val result_ko = cacheApi.get[Integer]("taz")

        result_ko must beNull[Integer]
      }
    }

    "get or else update" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[JavaSyncCacheApi]))
        //noinspection ConvertExpressionToSAM
        val orElse1: Callable[Integer] = new Callable[Integer] {
          override def call(): Integer = new Integer(1)
        }
        val result_ok: Integer = cacheApi.getOrElseUpdate[Integer]("paz", orElse1, 10)

        result_ok must beEqualTo(new Integer(1))

        //noinspection ConvertExpressionToSAM
        val orElse2: Callable[Integer] = new Callable[Integer] {
          override def call(): Integer = new Integer(2)
        }
        val result_eq: Integer = cacheApi.getOrElseUpdate[Integer]("paz", orElse2, 10)

        result_eq must beEqualTo(new Integer(1))
      }
    }

    "remove deletes it" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[JavaSyncCacheApi]))

        val result_ok: Integer = cacheApi.get[Integer]("foo")
        result_ok must beEqualTo(new Integer(1))

        cacheApi.remove("foo")

        val result_ko = cacheApi.get[Integer]("foo")
        result_ko must beNull[Integer]
      }
    }
  }

  "AkkaSerialization" should {

    def app = play.test.Helpers.fakeApplication(
      configurationMap.asJava
    )

    def injector = app.injector

    val now = System.currentTimeMillis()

    "serialize a complex Scala class" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[SyncCacheApi]))

        val value: ComplexTestItem = ComplexTestItem(
          "foo",
          None,
          Map(
            "bah" -> Seq(),
            "boh" -> Seq("i")
          ),
          SubItem("e", Date.from(Instant.ofEpochMilli(now))),
          Some(SubItem("e", Date.from(Instant.ofEpochMilli(now)))),
          active = true
        )

        val result: Seq[ComplexTestItem] = cacheApi.getOrElseUpdate[Seq[ComplexTestItem]]("complexitemseq", Duration(1, "minute")) {
          Seq(value)
        }

        result must not be empty
        result.head.id must beEqualTo("foo")

        val result2: Seq[ComplexTestItem] = cacheApi.getOrElseUpdate[Seq[ComplexTestItem]]("complexitemseq", Duration(1, "minute")) {
          Seq(value)
        }

        result2.head.id must beEqualTo(result.head.id)
      }
    }

    "serialize correctly empty sequences" in {
      implicit ee: ExecutionEnv => {
        val cacheApi = injector.instanceOf(play.api.inject.BindingKey(classOf[SyncCacheApi]))

        val result: Seq[ComplexTestItem] = cacheApi.getOrElseUpdate[Seq[ComplexTestItem]]("voidseq", Duration(1, "minute")) {
          Seq[ComplexTestItem]()
        }

        result must beEmpty

        val result2: Seq[ComplexTestItem] = cacheApi.getOrElseUpdate[Seq[ComplexTestItem]]("voidseq", Duration(1, "minute")) {
          Seq[ComplexTestItem]()
        }

        result2 must beEmpty
      }
    }
  }

}
