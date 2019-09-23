package org.goblinframework.cache.core.provider

import org.goblinframework.api.annotation.Singleton
import org.goblinframework.api.cache.GoblinCache
import org.goblinframework.api.cache.GoblinCacheBuilder

@Singleton
class InJvmCacheBuilder private constructor() : GoblinCacheBuilder {

  companion object {
    @JvmField val INSTANCE = InJvmCacheBuilder()
  }

  override fun decorateCacheName(name: String): String {
    return "JVM"
  }

  override fun getCache(name: String): GoblinCache? {
    return InJvmCache.INSTANCE
  }
}