package com.github.simonedeponti.play26lettuce

import play.api.Configuration


class LettuceClientProvider(val configuration: Configuration, name: String = "default") extends BaseClientProvider[LettuceCacheApi] {

  lazy val get: LettuceCacheApi = getLettuceApi(name)
}
