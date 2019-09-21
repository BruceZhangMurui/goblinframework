package org.goblinframework.remote.core.module

import org.goblinframework.api.annotation.Install
import org.goblinframework.core.bootstrap.GoblinModule
import org.goblinframework.core.bootstrap.GoblinModuleFinalizeContext
import org.goblinframework.core.bootstrap.GoblinModuleInitializeContext
import org.goblinframework.core.bootstrap.GoblinModuleInstallContext

@Install
class RemoteModule : GoblinModule {

  override fun name(): String {
    return "REMOTE"
  }

  override fun install(ctx: GoblinModuleInstallContext) {
    ctx.createChildModuleManager()
        .module("REMOTE:CLIENT")
        .next()
        .module("REMOTE:SERVER")
        .install(ctx)
  }

  override fun initialize(ctx: GoblinModuleInitializeContext) {
    ctx.createChildModuleManager()
        .module("REMOTE:CLIENT")
        .next()
        .module("REMOTE:SERVER")
        .initialize(ctx)
  }

  override fun finalize(ctx: GoblinModuleFinalizeContext) {
    ctx.createChildModuleManager()
        .module("REMOTE:SERVER")
        .next()
        .module("REMOTE:CLIENT")
        .finalize(ctx)
  }
}