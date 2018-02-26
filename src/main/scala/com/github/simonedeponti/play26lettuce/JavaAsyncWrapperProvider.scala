package com.github.simonedeponti.play26lettuce

import play.api.Configuration
import play.cache.AsyncCacheApi


/** Dependency-injection provider for [[play.cache.AsyncCacheApi]] wrapper
  *
  * @param configuration The application configuration
  * @param name The cache name (or "default" for the default one)
  */
class JavaAsyncWrapperProvider(val configuration: Configuration, name: String = "default") extends BaseClientProvider[AsyncCacheApi] {

  lazy val get: AsyncCacheApi = {
    new JavaAsyncWrapper(getLettuceApi(name), configuration)(ec)
  }

}