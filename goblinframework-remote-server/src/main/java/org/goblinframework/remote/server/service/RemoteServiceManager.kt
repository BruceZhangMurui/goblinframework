package org.goblinframework.remote.server.service

import org.goblinframework.api.annotation.Singleton
import org.goblinframework.api.annotation.ThreadSafe
import org.goblinframework.core.exception.GoblinDuplicateException
import org.goblinframework.core.mbean.GoblinManagedBean
import org.goblinframework.core.mbean.GoblinManagedObject
import org.goblinframework.remote.server.expose.ExposeServiceId
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Singleton
@ThreadSafe
@GoblinManagedBean("REMOTE.SERVER")
class RemoteServiceManager private constructor()
  : GoblinManagedObject(), RemoteServiceManagerMXBean {

  companion object {
    @JvmField val INSTANCE = RemoteServiceManager()
  }

  private val lock = ReentrantReadWriteLock()
  private val services = mutableMapOf<ExposeServiceId, RemoteService>()

  fun register(service: RemoteService) {
    val id = service.id()
    lock.write {
      services[id]?.run {
        throw GoblinDuplicateException("Duplicated expose service id: $id")
      }
      services[id] = service
    }
  }

  fun remoteService(id: ExposeServiceId): RemoteService? {
    return lock.read { services[id] }
  }

  fun close() {
    unregisterIfNecessary()
    lock.write {
      services.values.forEach { it.close() }
      services.clear()
    }
  }
}