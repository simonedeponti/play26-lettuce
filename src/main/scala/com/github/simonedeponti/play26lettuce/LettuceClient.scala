package com.github.simonedeponti.play26lettuce

import javax.inject.{Inject, Singleton}

import akka.Done

import scala.reflect.ClassTag
import scala.compat.java8.FutureConverters._
import akka.actor.ActorSystem
import akka.serialization.SerializationExtension
import io.lettuce.core.{RedisClient, SetArgs}
import io.lettuce.core.api.async.RedisAsyncCommands
import play.api.Configuration

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}


@Singleton
class LettuceClient @Inject() (system: ActorSystem, configuration: Configuration, name: String = "default")
                              (implicit val ec: ExecutionContext) extends LettuceCacheApi {

  private val client: RedisClient = RedisClient.create(configuration.get[String]("lettuce.url"))
  private val commands: RedisAsyncCommands[String, String] = client.connect().async()
  private val serialization = SerializationExtension(system)

  private def deserialize[T](data: String, ctag: ClassTag[T]): T = {
    serialization.deserialize[T](data.getBytes("UTF-8"), ctag.runtimeClass.asInstanceOf[Class[T]]) match {
      case Success(v) => v
      case Failure(e) => throw e
    }
  }

  private def serialize(obj: Any): String = {
    serialization.serialize(obj.asInstanceOf[AnyRef]) match {
      case Success(s) => s.map(_.toChar).mkString
      case Failure(e) => throw e
    }
  }

  override def get[T](key: String)(implicit ctag: ClassTag[T]): Future[Option[T]] = {
    commands.get(key).toScala map {
      case (data: String) =>
        if(data == null) {
          Some(deserialize[T](data, ctag))
        }
        else {
          None
        }
    }
  }

  override def getOrElseUpdate[A](key: String, expiration: Duration)(orElse: => Future[A])(implicit ctag: ClassTag[A]): Future[A] = {
    commands.get(key).toScala.flatMap({
      case (data: String) =>
        if(data == null) {
          Future(deserialize[A](data, ctag))
        }
        else {
          orElse.flatMap(value => {
            commands.set(
              key,
              serialize(value),
              SetArgs.Builder.ex(expiration.toSeconds)
            ).toScala.map(_ => value)
          })
        }
    })
  }

  override def set(key: String, value: Any, expiration: Duration): Future[Done] = {
    commands.set(
      key,
      serialize(value),
      SetArgs.Builder.ex(expiration.toSeconds)
    ).toScala.map(_ => Done)
  }

  override def remove(key: String): Future[Done] = {
    commands.del(key).toScala.map(_ => Done)
  }

  override def removeAll(): Future[Done] = {
    commands.flushall().toScala.map(_ => Done)
  }

}