package com.github.simonedeponti.play26lettuce

import play.api.Configuration
import play.cache.AsyncCacheApi


class JavaAsyncWrapperProvider(val configuration: Configuration, name: String = "default") extends BaseClientProvider[AsyncCacheApi] {

  lazy val get: AsyncCacheApi = {
    new JavaAsyncWrapper(getLettuceApi(name))(ec)
  }

}