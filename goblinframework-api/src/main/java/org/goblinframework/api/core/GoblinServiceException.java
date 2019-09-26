package org.goblinframework.api.core;

public class GoblinServiceException extends GoblinException {
  private static final long serialVersionUID = 402921636817030857L;

  public GoblinServiceException() {
  }

  public GoblinServiceException(String message) {
    super(message);
  }

  public GoblinServiceException(String message, Throwable cause) {
    super(message, cause);
  }

  public GoblinServiceException(Throwable cause) {
    super(cause);
  }
}
