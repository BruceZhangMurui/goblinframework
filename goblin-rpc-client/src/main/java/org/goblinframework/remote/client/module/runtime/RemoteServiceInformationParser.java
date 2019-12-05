package org.goblinframework.remote.client.module.runtime;

import org.goblinframework.api.concurrent.GoblinFuture;
import org.goblinframework.api.core.SerializerMode;
import org.goblinframework.api.reactor.GoblinPublisher;
import org.goblinframework.api.rpc.NoResponseWait;
import org.goblinframework.remote.client.module.config.RemoteClientConfig;
import org.goblinframework.remote.client.module.config.RemoteClientConfigManager;
import org.goblinframework.remote.core.util.ServiceEncoderUtils;
import org.goblinframework.remote.core.util.ServiceRetriesUtils;
import org.goblinframework.remote.core.util.ServiceTimeoutUtils;
import org.goblinframework.remote.core.util.ServiceVersionUtils;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

final class RemoteServiceInformationParser {

  @NotNull
  static RemoteServiceInformation parse(@NotNull Class<?> interfaceClass) {
    RemoteClientConfig clientConfig = RemoteClientConfigManager.INSTANCE.getRemoteClientConfig();

    String version = ServiceVersionUtils.calculateServerVersion(interfaceClass);
    long timeout = ServiceTimeoutUtils.calculateServiceTimeout(interfaceClass, clientConfig.getMaxTimeout());
    int retries = ServiceRetriesUtils.calculateServiceRetries(interfaceClass);
    SerializerMode encoder = ServiceEncoderUtils.calculateServiceEncoder(interfaceClass, clientConfig.getSerializer());

    RemoteServiceInformation information = new RemoteServiceInformation();
    information.interfaceClass = interfaceClass;
    information.version = version;
    information.timeout = timeout;
    information.retries = retries;
    information.encoder = encoder;
    for (Method method : interfaceClass.getMethods()) {
      RemoteServiceMethodInformation methodInformation = parse(method, information);
      information.methodInformationMap.put(method, methodInformation);
    }
    return information;
  }

  private static RemoteServiceMethodInformation parse(Method method,
                                                      RemoteServiceInformation serviceInformation) {
    Long timeout = ServiceTimeoutUtils.calculateServiceTimeout(method);
    Integer retries = ServiceRetriesUtils.calculateServiceRetries(method);
    SerializerMode encoder = ServiceEncoderUtils.calculateServiceEncoder(method);
    boolean asynchronous = false;
    boolean noResponseWait = false;
    boolean dispatchAll = false;
    boolean ignoreNoProvider = false;

    Class<?> returnType = method.getReturnType();
    if (returnType == void.class) {
      NoResponseWait noResponseWaitAnnotation = method.getAnnotation(NoResponseWait.class);
      if (noResponseWaitAnnotation != null && noResponseWaitAnnotation.enable()) {
        noResponseWait = true;
        dispatchAll = noResponseWaitAnnotation.dispatchAll();
        ignoreNoProvider = noResponseWaitAnnotation.ignoreNoProvider();
      }
    } else {
      if (returnType == GoblinFuture.class || returnType == GoblinPublisher.class) {
        asynchronous = true;
      }
    }

    RemoteServiceMethodInformation information = new RemoteServiceMethodInformation(serviceInformation, method);
    information.timeout = timeout;
    information.retries = retries;
    information.encoder = encoder;
    information.asynchronous = asynchronous;
    information.noResponseWait = noResponseWait;
    information.dispatchAll = dispatchAll;
    information.ignoreNoProvider = ignoreNoProvider;
    return information;
  }
}
