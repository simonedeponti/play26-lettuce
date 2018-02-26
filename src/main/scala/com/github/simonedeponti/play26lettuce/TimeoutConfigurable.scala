package com.github.simonedeponti.play26lettuce

import java.util.concurrent.TimeUnit

import play.api.Configuration

import scala.concurrent.duration.Duration


/** Defines methods to get the future's timeout to use in sync APIs.
  *
  * This trait should be used by wrappers only.
  */
trait TimeoutConfigurable {
  /** The [[com.github.simonedeponti.play26lettuce.LettuceCacheApi]] instance **/
  val acache: LettuceCacheApi
  /** The application configuration **/
  val configuration: Configuration

  /** Fetches the timeout to use when awaiting results syncronously from the application configuration.
    *
    * @return The timeout to be used when awaiting results syncronously
    */
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
