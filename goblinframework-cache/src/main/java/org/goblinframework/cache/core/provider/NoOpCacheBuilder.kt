package org.goblinframework.cache.core.provider

import org.goblinframework.api.annotation.Singleton
import org.goblinframework.cache.core.Cache
import org.goblinframework.cache.core.CacheBuilder
import org.goblinframework.cache.core.CacheSystem

@Singleton
class NoOpCacheBuilder private constructor() : CacheBuilder {

  companion object {
    @JvmField val INSTANCE = NoOpCacheBuilder()
  }

  override fun getCacheSystem(): CacheSystem {
    return CacheSystem.NOP
  }

  override fun getCache(name: String): Cache {
    return NoOpCache.INSTANCE
  }
}