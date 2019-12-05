package org.goblinframework.cache.core.support;

import java.util.LinkedList;
import java.util.List;

public class CacheMethod {

  public Class<?> type;

  public final List<CacheMethodParameter> parameterList = new LinkedList<>();
  public int annotationCount = 0;
  public int multipleCount = 0;
  public int multipleIndex = -1;
  public int parameterCount = 0;

}