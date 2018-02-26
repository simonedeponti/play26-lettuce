package com.github.simonedeponti.play26lettuce

import play.api.Configuration
import play.cache.SyncCacheApi


/** Dependency-injection provider for [[play.cache.SyncCacheApi]] wrapper
  *
  * @param configuration Application configuration
  * @param name The cache name (if none provided uses "default")
  */
class JavaSyncWrapperProvider(val configuration: Configuration, name: String = "default") extends BaseClientProvider[SyncCacheApi] {

  lazy val get: SyncCacheApi = {
    new JavaSyncWrapper(getLettuceApi(name), configuration)(ec)
  }

}