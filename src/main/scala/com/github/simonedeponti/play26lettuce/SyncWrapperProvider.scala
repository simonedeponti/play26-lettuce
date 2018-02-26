package com.github.simonedeponti.play26lettuce

import play.api.Configuration
import play.api.cache.SyncCacheApi


/** Dependency-injection provider for [[play.api.cache.SyncCacheApi]]
  *
  * @param configuration The application configuration
  * @param name The cache name (if not provided uses "default")
  */
class SyncWrapperProvider(val configuration: Configuration, val name: String = "default") extends BaseClientProvider[SyncCacheApi] {

  lazy val get: SyncCacheApi = {
    new SyncWrapper(getLettuceApi(name), configuration)(ec)
  }

}
