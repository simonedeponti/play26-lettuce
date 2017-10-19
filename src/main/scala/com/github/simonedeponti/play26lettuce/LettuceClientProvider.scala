package com.github.simonedeponti.play26lettuce

import javax.inject.{Inject, Provider}

import akka.actor.ActorSystem
import play.api.Configuration
import play.api.cache.AsyncCacheApi
import play.api.inject.Injector

import scala.concurrent.ExecutionContext


class LettuceClientProvider(configuration: Configuration, name: String = "default") extends Provider[AsyncCacheApi] {
  @Inject private var injector: Injector = _
  @Inject private var actorSystem: ActorSystem = _

  private lazy val ec: ExecutionContext = configuration.get[Option[String]]("play.cache.dispatcher").map(actorSystem.dispatchers.lookup(_)).getOrElse(injector.instanceOf[ExecutionContext])

  lazy val get: AsyncCacheApi = {
    new LettuceClient(actorSystem, configuration, name)(ec)
  }
}
