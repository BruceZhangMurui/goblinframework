package org.goblinframework.api.system;

import org.goblinframework.api.core.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Internal
public interface IModule {

  @NotNull
  GoblinModule id();

  @Nullable
  default String managementEntrance() {
    return null;
  }

  default void install(@NotNull ModuleInstallContext ctx) {
  }

  default void initialize(@NotNull ModuleInitializeContext ctx) {
  }

  default void finalize(@NotNull ModuleFinalizeContext ctx) {
  }
}
