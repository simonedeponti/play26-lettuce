package com.github.simonedeponti.play26lettuce

import javax.inject.{Inject, Singleton}

import akka.Done

import scala.reflect.ClassTag
import scala.compat.java8.FutureConverters._
import akka.actor.ActorSystem
import akka.serialization.SerializationExtension
import io.lettuce.core.{RedisClient, SetArgs}
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.codec.ByteArrayCodec
import play.api.Configuration

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}


@Singleton
class LettuceClient @Inject() (val system: ActorSystem, val configuration: Configuration, val name: String = "default")
                              (implicit val ec: ExecutionContext) extends LettuceCacheApi {

  private val client: RedisClient = RedisClient.create(configuration.get[String](s"lettuce.$name.url"))
  private val codec = new ByteArrayCodec
  private val commands: RedisAsyncCommands[Array[Byte], Array[Byte]] = client.connect(codec).async()
  private val serialization = SerializationExtension(system)

  private def deserialize[T](data: Array[Byte], ctag: ClassTag[T]): T = {
    serialization.deserialize[T](data, ctag.runtimeClass.asInstanceOf[Class[T]]) match {
      case Success(v) => v
      case Failure(e) => throw e
    }
  }

  private def serialize(obj: Any): Array[Byte] = {
    serialization.serialize(obj.asInstanceOf[AnyRef]) match {
      case Success(s) => s
      case Failure(e) => throw e
    }
  }

  private def doSet(key: String, value: Any, expiration: Duration): Future[Any] = {
    expiration match {
      case Duration.Inf =>
        commands.set(
          s"$name::$key".getBytes("UTF-8"),
          serialize(value)
        ).toScala
      case _ =>
        commands.set(
          s"$name::$key".getBytes("UTF-8"),
          serialize(value),
          SetArgs.Builder.ex(expiration.toSeconds)
        ).toScala
    }
  }

  override def get[T](key: String)(implicit ctag: ClassTag[T]): Future[Option[T]] = {
    commands.get(s"$name::$key".getBytes("UTF-8")).toScala map {
      case (data: Array[Byte]) =>
        Some(deserialize[T](data, ctag))
      case null => None
    }
  }

  override def getOrElseUpdate[A](key: String, expiration: Duration)(orElse: => Future[A])(implicit ctag: ClassTag[A]): Future[A] = {
    commands.get(s"$name::$key".getBytes("UTF-8")).toScala.flatMap({
      case (data: Array[Byte]) =>
        if(data == null) {
          Future(deserialize[A](data, ctag))
        }
        else {
          orElse.flatMap(value => {
            doSet(key, value, expiration).map(_ => Done).map(_ => value)
          })
        }
    })
  }

  override def set(key: String, value: Any, expiration: Duration): Future[Done] = {
    doSet(key, value, expiration).map(_ => Done)
  }

  override def remove(key: String): Future[Done] = {
    commands.del(s"$name::$key".getBytes("UTF-8")).toScala.map(_ => Done)
  }

  override def removeAll(): Future[Done] = {
    commands.keys(s"$name::*".getBytes("UTF-8")).toScala.flatMap(
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