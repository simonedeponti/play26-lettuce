package com.github.simonedeponti.play26lettuce

import javax.inject.{Inject, Provider}

import akka.actor.ActorSystem
import play.api.Configuration
import play.api.inject.Injector

import scala.concurrent.ExecutionContext


abstract class BaseClientProvider[T] extends Provider[T] {
  val configuration: Configuration

  @Inject protected var injector: Injector = _
  @Inject protected var actorSystem: ActorSystem = _

  protected def ec: ExecutionContext = configuration.getOptional[String]("play.cache.dispatcher").map(actorSystem.dispatchers.lookup(_)).getOrElse(injector.instanceOf[ExecutionContext])


  protected def getLettuceApi(name: String): LettuceCacheApi = {
    new LettuceClient(actorSystem, configuration, name)(ec)
  }
}
