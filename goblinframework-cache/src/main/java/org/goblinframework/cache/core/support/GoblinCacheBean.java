package org.goblinframework.cache.core.support;

import org.goblinframework.cache.core.annotation.CacheSystem;
import org.goblinframework.cache.core.annotation.GoblinCacheExpiration;
import org.goblinframework.cache.core.cache.CacheSystemLocation;
import org.goblinframework.cache.core.module.GoblinCacheException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GoblinCacheBean {

  private final IdentityHashMap<Class<?>, GoblinCache> caches = new IdentityHashMap<>();
  final Map<Method, GoblinCacheMethod> methods = new LinkedHashMap<>();

  public GoblinCacheBean() {
  }

  public GoblinCacheBean(@NotNull List<org.goblinframework.cache.core.annotation.GoblinCacheBean> annotations) {
    for (org.goblinframework.cache.core.annotation.GoblinCacheBean annotation : annotations) {
      GoblinCache gc = new GoblinCache();
      gc.type = annotation.type();
      gc.location = new CacheSystemLocation(annotation.system(), annotation.name());
      gc.wrapper = annotation.wrapper();
      GoblinCacheExpiration expiration = annotation.expiration();
      if (expiration.enable()) {
        gc.expirationPolicy = expiration.policy();
        gc.expirationValue = expiration.value();
      }
      CacheSystem cacheSystem = gc.location.getSystem();
      String cacheName = gc.location.getName();
      org.goblinframework.cache.core.cache.GoblinCache cache = cacheSystem.getCache(cacheName);
      if (cache == null) {
        String errMsg = "GOBLIN cache [%s/%s] not available";
        errMsg = String.format(errMsg, cacheSystem, cacheName);
        throw new GoblinCacheException(errMsg);
      }
      gc.cache = cache;
      caches.put(gc.type, gc);
    }
  }

  public boolean isEmpty() {
    return caches.isEmpty();
  }

  @Nullable
  public GoblinCache getGoblinCache(@NotNull Class<?> type) {
    return caches.get(type);
  }

  public GoblinCacheMethod getGoblinCacheMethod(Method method) {
    return methods.get(method);
  }
}
