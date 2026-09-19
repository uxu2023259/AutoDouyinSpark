package awa.uxu.douyin.autospark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BundledBrowserTest {
  @TempDir
  Path tempDir;

  @Test
  void findsChromeInBundledBrowsersDirectory() throws Exception {
    Path chrome = tempDir.resolve("browsers").resolve("chromium-1217").resolve("chrome-win64").resolve("chrome.exe");
    Files.createDirectories(chrome.getParent());
    Files.writeString(chrome, "模拟浏览器文件");

    assertEquals(chrome, ChatAutomator.findBundledChrome(tempDir, null));
  }

  @Test
  void findsChromeWhenSearchRootIsExecutablePath() throws Exception {
    Path appRoot = tempDir.resolve("抖音自动续火花助手");
    Path exe = appRoot.resolve("抖音自动续火花助手.exe");
    Path chrome = appRoot.resolve("browsers").resolve("chromium-1217").resolve("chrome-win64").resolve("chrome.exe");
    Files.createDirectories(chrome.getParent());
    Files.writeString(exe, "模拟程序文件");
    Files.writeString(chrome, "模拟浏览器文件");

    assertEquals(chrome, ChatAutomator.findBundledChrome(exe.getParent(), null));
  }

  @Test
  void findsChromeInAppBrowsersDirectory() throws Exception {
    Path chrome = tempDir.resolve("app").resolve("browsers").resolve("chromium-1217").resolve("chrome-win64").resolve("chrome.exe");
    Files.createDirectories(chrome.getParent());
    Files.writeString(chrome, "模拟浏览器文件");

    assertEquals(chrome, ChatAutomator.findBundledChrome(tempDir, null));
  }

  @Test
  void returnsNullWhenBundledBrowserMissing() {
    assertNull(ChatAutomator.findBundledChrome(tempDir, null));
  }
}
