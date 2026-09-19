package awa.uxu.douyin.autospark;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserProfileGuardTest {
  @Test
  void recognizesChromiumExitCodeTwentyOneAsProfileConflict() {
    RuntimeException error = new RuntimeException("process did exit: exitCode=21, signal=null");

    assertTrue(ChatAutomator.isProfileConflict(error));
    assertTrue(ChatAutomator.summarizeAutomationFailure(error).contains("登录目录"));
  }

  @Test
  void doesNotMisclassifyOrdinaryTimeoutAsProfileConflict() {
    RuntimeException error = new RuntimeException("Timeout 45000ms exceeded");

    assertFalse(ChatAutomator.isProfileConflict(error));
    assertTrue(ChatAutomator.summarizeAutomationFailure(error).contains("等待超时"));
    assertFalse(ChatAutomator.summarizeAutomationFailure(error).contains("Timeout"));
    assertFalse(ChatAutomator.isControlConnectionFailure(error));
  }

  @Test
  void onlyClosedControlConnectionsTriggerBrowserRebuild() {
    RuntimeException closed = new RuntimeException("Target page, context or browser has been closed");
    RuntimeException detachedNode = new RuntimeException("Element is not attached to the DOM");

    assertTrue(ChatAutomator.isControlConnectionFailure(closed));
    assertFalse(ChatAutomator.isControlConnectionFailure(detachedNode));
  }

  @Test
  void normalizesWindowsProfilePathForCommandLineDetection() {
    Path profile = Path.of("C:\\Users\\Administrator\\AppData\\Roaming\\DouyinAutoSpark\\accounts\\default\\browser-profile");
    String commandLine = "chrome.exe --user-data-dir=\"C:\\Users\\Administrator\\AppData\\Roaming\\DouyinAutoSpark\\accounts\\default\\browser-profile\"";

    assertTrue(BrowserProfileGuard.normalizeCommandLine(commandLine).contains(BrowserProfileGuard.normalize(profile)));
  }
}
