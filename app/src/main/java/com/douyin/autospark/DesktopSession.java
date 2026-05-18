package com.douyin.autospark;

import java.awt.GraphicsEnvironment;

public final class DesktopSession {
  private DesktopSession() {
  }

  public static boolean isInteractiveDesktopAvailable() {
    if (GraphicsEnvironment.isHeadless()) {
      return false;
    }
    String sessionName = System.getenv("SESSIONNAME");
    return sessionName == null || !"Services".equalsIgnoreCase(sessionName.trim());
  }
}
