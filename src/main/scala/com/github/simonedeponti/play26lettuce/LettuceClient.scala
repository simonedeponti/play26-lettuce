package com.github.simonedeponti.play26lettuce

import java.util

import javax.inject.{ Inject, Singleton }
import akka.Done

import scala.reflect.ClassTag
import scala.compat.java8.FutureConverters._
import akka.actor.ActorSystem
import io.lettuce.core.{ KeyValue, RedisClient, RedisFuture, SetArgs }
import io.lettuce.core.api.async.RedisAsyncCommands
import play.api.Configuration

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.Duration


/** The base implementation of [[com.github.simonedeponti.play26lettuce.LettuceCacheApi]].
  *
  * @param system The current Akka actor system
  * @param configuration The application configuration
  * @param name The cache name (or "default" if missing)
  * @param ec The execution context to use
  */
@Singleton
class LettuceClient @Inject() (val system: ActorSystem, val configuration: Configuration, val name: String = "default")
                              (implicit val ec: ExecutionContext) extends LettuceCacheApi {

  /** The [[io.lettuce.core.RedisClient]] instance that represents the connection to Redis **/
  private val client: RedisClient = RedisClient.create(configuration.get[String](s"lettuce.$name.url"))
  /** The serialization codec (an [[com.github.simonedeponti.play26lettuce.AkkaCodec]] instance) **/
  private val codec = new AkkaCodec(system)
  /** The redis commands bound to the specific client and encoder **/
  private val commands: RedisAsyncCommands[String, AnyRef] = client.connect(codec).async()

  /** Do a set on Redis: these are two different method calls depending
    * on what the duration is (infinite is a special case)
    *
    * @param key The key to set
    * @param value The value to set for the key
    * @param expiration The TTL of the entry
    * @return The Redis command result
    */
  private def doSet(key: String, value: Any, expiration: Duration): Future[Any] = {
    expiration match {
      case Duration.Inf =>
        commands.set(
          s"$name::$key",
          value.asInstanceOf[AnyRef]
        ).toScala
      case _ =>
        commands.set(
          s"$name::$key",
          value.asInstanceOf[AnyRef],
          SetArgs.Builder.ex(expiration.toSeconds)
        ).toScala
    }
  }

  override def javaGet[T](key: String): Future[Option[T]] = {
    commands.get(s"$name::$key").toScala map {
      case (data: AnyRef) =>
        Some(data.asInstanceOf[T])
      case null => None
    }
  }

  override def get[T](key: String)(implicit ctag: ClassTag[T]): Future[Option[T]] = {
    javaGet[T](key)
  }

  override def getAll[T <: AnyRef](keys: Seq[String]): Future[Seq[Option[T]]] = {
    commands.mget(keys.map(key => s"$name::$key"): _*).toScala.map(_.asScala.map {
      case data: KeyValue[String, AnyRef] if data.hasValue => Some(data.getValue).asInstanceOf[Option[T]]
      case data: KeyValue[String, AnyRef] if !data.hasValue => None
      case null => None
    })
  }

  override def getOrElseUpdate[A](key: String, expiration: Duration)(orElse: => Future[A])(implicit ctag: ClassTag[A]): Future[A] = {
    javaGetOrElseUpdate[A](key, expiration)(orElse)
  }

  override def javaGetOrElseUpdate[A](key: String, expiration: Duration)(orElse: => Future[A]): Future[A] = {
    commands.get(s"$name::$key").toScala.flatMap({
      case (data: AnyRef) => Future(data.asInstanceOf[A])
      case null =>
        orElse.flatMap(value => {
          doSet(key, value, expiration).map(_ => Done).map(_ => value)
        })
    })
  }

  override def set(key: String, value: Any, expiration: Duration): Future[Done] = {
    doSet(key, value, expiration).map(_ => Done)
  }

  override def setAll(keyValues: Map[String, AnyRef]): Future[Done] = {
    commands.mset(
      mutable.Map(
        keyValues.map {
          case (key, value) => (s"$name::$key", value)
        }.toSeq: _*
      ).asJava
    ).toScala.map(_ => Done)
  }

  override def remove(key: String): Future[Done] = {
    commands.del(s"$name::$key").toScala.map(_ => Done)
  }

  override def remove(keys: Seq[String]): Future[Done] = {
    commands.del(keys.map(key => s"$name::$key"): _*).toScala.map(_ => Done)
  }

  override def removeAll(): Future[Done] = {
    commands.keys(s"$name::*").toScala.flatMap(
      keys => {
        Future.sequence(
          keys.asScala.map(
            key => commands.del(key).toScala
          )
        ).map(_ => Done)
      }
    )
  }
}
