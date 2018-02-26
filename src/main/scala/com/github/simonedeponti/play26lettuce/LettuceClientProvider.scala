package com.github.simonedeponti.play26lettuce

import play.api.Configuration


/** Dependency-injection provider for [[LettuceCacheApi]].
  *
  * This is never called directly, but it is called indirectly by all wrappers.
  *
  * @param configuration The application configuration
  * @param name The cache name (if not provided uses "default")
  */
class LettuceClientProvider(val configuration: Configuration, name: String = "default") extends BaseClientProvider[LettuceCacheApi] {

  lazy val get: LettuceCacheApi = getLettuceApi(name)
}
