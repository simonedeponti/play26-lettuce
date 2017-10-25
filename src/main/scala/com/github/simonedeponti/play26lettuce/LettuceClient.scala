package com.github.simonedeponti.play26lettuce

import java.nio.ByteBuffer
import javax.inject.{Inject, Singleton}

import akka.Done

import scala.reflect.ClassTag
import scala.compat.java8.FutureConverters._
import akka.actor.ActorSystem
import akka.serialization.SerializationExtension
import io.lettuce.core.{RedisClient, SetArgs}
import io.lettuce.core.api.async.RedisAsyncCommands
import play.api.Configuration

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration


@Singleton
class LettuceClient @Inject() (val system: ActorSystem, val configuration: Configuration, val name: String = "default")
                              (implicit val ec: ExecutionContext) extends LettuceCacheApi {

  private val client: RedisClient = RedisClient.create(configuration.get[String](s"lettuce.$name.url"))
  private val codec = new AkkaCodec(system)
  private val commands: RedisAsyncCommands[String, AnyRef] = client.connect(codec).async()
  private val serialization = SerializationExtension(system)

  private def deserialize[T](data: Array[Byte]): T = {
    val header = data.take(4)
    val payload = data.takeRight(4)
    val serializerId = ByteBuffer.wrap(header).getInt
    val serializer = serialization.serializerByIdentity(serializerId)
    serializer.fromBinary(payload, manifest = None).asInstanceOf[T]
  }

  private def serialize(obj: Any): Array[Byte] = {
    def serializer = serialization.findSerializerFor(obj.asInstanceOf[AnyRef])
    def header = ByteBuffer.allocate(4).putInt(serializer.identifier).array()
    def payload = serializer.toBinary(obj.asInstanceOf[AnyRef])
    header ++ payload
  }

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

  override def remove(key: String): Future[Done] = {
    commands.del(s"$name::$key").toScala.map(_ => Done)
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