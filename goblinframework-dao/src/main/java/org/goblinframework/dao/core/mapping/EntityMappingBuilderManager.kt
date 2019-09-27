package org.goblinframework.dao.core.mapping

import org.goblinframework.api.core.GoblinManagedObject
import org.goblinframework.api.core.ServiceInstaller
import org.goblinframework.api.core.Singleton
import org.goblinframework.core.exception.GoblinDuplicateException
import org.goblinframework.dao.core.GoblinDatabaseSystem
import java.util.*

@Singleton
class EntityMappingBuilderManager private constructor()
  : GoblinManagedObject(), EntityMappingBuilderManagerMXBean {

  companion object {
    @JvmField val INSTANCE = EntityMappingBuilderManager()
  }

  private val buffer = EnumMap<GoblinDatabaseSystem, EntityMappingBuilder>(GoblinDatabaseSystem::class.java)

  init {
    ServiceInstaller.asList(EntityMappingBuilderProvider::class.java).forEach {
      val s = it.databaseSystem
      if (buffer[s] != null) {
        throw GoblinDuplicateException()
      }
      buffer[s] = it.entityMappingBuilder
    }
  }

  fun getEntityMappingBuilder(databaseSystem: GoblinDatabaseSystem): EntityMappingBuilder? {
    return buffer[databaseSystem]
  }

  override fun disposeBean() {
    buffer.values.forEach { it.dispose() }
  }
}