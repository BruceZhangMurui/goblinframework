package org.goblinframework.dao.mysql.module.config

import org.goblinframework.core.config.Config
import org.goblinframework.api.service.GoblinManagedBean
import org.goblinframework.core.mbean.GoblinManagedObject

@GoblinManagedBean("DAO.MYSQL")
class MysqlConfig(private val name: String,
                  private val master: DataSourceConfig,
                  private val slaves: List<DataSourceConfig>)
  : GoblinManagedObject(), MysqlConfigMXBean, Config {

  override fun disposeBean() {
    master.dispose()
    slaves.forEach { it.dispose() }
  }

  override fun getName(): String {
    return name
  }

  override fun getMaster(): DataSourceConfigMXBean {
    return master
  }

  override fun getSlaveList(): Array<DataSourceConfigMXBean> {
    return slaves.toTypedArray()
  }
}