package com.github.simonedeponti.play26lettuce

import play.api.Configuration
import play.cache.SyncCacheApi


class JavaSyncWrapperProvider(val configuration: Configuration, name: String = "default") extends BaseClientProvider[SyncCacheApi] {

  lazy val get: SyncCacheApi = {
    new JavaSyncWrapper(getLettuceApi(name), configuration)(ec)
  }

}