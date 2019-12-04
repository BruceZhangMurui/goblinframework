package org.goblinframework.cache.core.cache

import org.goblinframework.api.annotation.Singleton
import org.goblinframework.cache.core.CacheBuilder
import org.goblinframework.cache.core.CacheSystem
import org.goblinframework.core.service.GoblinManagedBean
import org.goblinframework.core.service.GoblinManagedObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Management center of [CacheBuilder].
 *
 * @author Xiaohai Zhang
 * @since Dec 4, 2019
 */
@Singleton
@GoblinManagedBean("Cache")
class CacheBuilderManager2 private constructor()
  : GoblinManagedObject(), CacheBuilderManagerMXBean {

  companion object {
    @JvmField val INSTANCE = CacheBuilderManager2()
  }

  private val buffer = ConcurrentHashMap<CacheSystem, CacheBuilder>()

  fun registerCacheBuilder(cacheBuilder: CacheBuilder) {
    val cacheSystem = cacheBuilder.getCacheSystem()
    buffer.putIfAbsent(cacheSystem, cacheBuilder)?.run {
      throw IllegalStateException("CacheBuilder [$cacheSystem] already registered")
    }
    logger.debug("{Cache} CacheBuilder [$cacheSystem] registered")
  }

  fun unregisterCacheBuilder(cacheBuilder: CacheBuilder) {
    val cacheSystem = cacheBuilder.getCacheSystem()
    buffer.remove(cacheSystem)?.run {
      logger.debug("{Cache} CacheBuilder [$cacheSystem] unregistered")
    }
  }

  fun getCacheBuilder(cacheSystem: CacheSystem): CacheBuilder? {
    return buffer[cacheSystem]
  }

  override fun getCacheBuilderList(): Array<CacheBuilderMXBean> {
    return buffer.values
        .filterIsInstance(CacheBuilderMXBean::class.java)
        .sortedBy { it.getCacheSystem() }
        .toTypedArray()
  }

  override fun disposeBean() {
    buffer.clear()
  }
}