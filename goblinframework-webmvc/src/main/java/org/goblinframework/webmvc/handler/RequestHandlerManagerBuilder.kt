package org.goblinframework.webmvc.handler

import org.goblinframework.api.common.DuplicateException
import org.goblinframework.core.mbean.GoblinManagedBean
import org.goblinframework.core.mbean.GoblinManagedObject
import org.goblinframework.webmvc.setting.RequestHandlerSetting
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

@GoblinManagedBean("WEBMVC")
class RequestHandlerManagerBuilder private constructor()
  : GoblinManagedObject(), RequestHandlerManagerBuilderMXBean {

  companion object {
    val INSTANCE = RequestHandlerManagerBuilder()
  }

  private val lock = ReentrantReadWriteLock()
  private val buffer = mutableMapOf<String, RequestHandlerManager>()

  fun createRequestHandlerManager(setting: RequestHandlerSetting): RequestHandlerManager {
    val name = setting.name()
    lock.write {
      buffer[name]?.run {
        throw DuplicateException("RequestHandlerManager [$name] already created")
      }
      val manager = RequestHandlerManager(setting)
      buffer[name] = manager
      return manager
    }
  }

  fun close() {
    unregisterIfNecessary()
    lock.write {
      buffer.values.forEach { it.close() }
      buffer.clear()
    }
  }
}