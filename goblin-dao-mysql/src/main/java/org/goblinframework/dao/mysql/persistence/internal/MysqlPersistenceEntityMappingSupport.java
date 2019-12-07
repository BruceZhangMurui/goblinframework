package org.goblinframework.dao.mysql.persistence.internal;

import org.goblinframework.dao.core.persistence.PersistenceListenerSupport;
import org.goblinframework.dao.mysql.mapping.MysqlEntityMappingBuilder;
import org.goblinframework.database.core.mapping.EntityMappingBuilder;
import org.jetbrains.annotations.NotNull;

abstract public class MysqlPersistenceEntityMappingSupport<E, ID> extends PersistenceListenerSupport<E, ID> {

  @NotNull
  @Override
  protected EntityMappingBuilder getEntityMappingBuilder() {
    return MysqlEntityMappingBuilder.INSTANCE;
  }
}