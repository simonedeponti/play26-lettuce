package com.github.simonedeponti.play26lettuce

import java.util.concurrent.TimeUnit

import play.api.Configuration

import scala.concurrent.duration.Duration


trait TimeoutConfigurable {
  val acache: LettuceCacheApi
  val configuration: Configuration

  def timeout: Duration = Duration(
    if(configuration.has(s"lettuce.${acache.name}.syncTimeout")) {
      configuration.getMillis(s"lettuce.${acache.name}.syncTimeout")
    }
    else {
      1000
    },
    TimeUnit.MILLISECONDS
  )
}
