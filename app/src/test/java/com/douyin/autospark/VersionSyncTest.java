package com.douyin.autospark;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionSyncTest {
  @Test
  void allVersionFilesStayInSync() throws Exception {
    String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
    String plugin = Files.readString(Path.of("plugin.yml"), StandardCharsets.UTF_8);
    String version = Files.readString(Path.of("build", "version.json"), StandardCharsets.UTF_8);

    String pomVersion = firstMatch(pom, "<artifactId>douyin-auto-spark</artifactId>\\s*<version>([^<]+)</version>");
    String pluginVersion = firstMatch(plugin, "(?m)^version:\\s*([^\\r\\n]+)");
    String buildVersion = firstMatch(version, "\\\"version\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    assertTrue(pomVersion.matches("\\d+\\.\\d+\\.\\d+"));
    assertEquals(pomVersion, pluginVersion);
    assertEquals(pomVersion, buildVersion);
  }

  private String firstMatch(String text, String pattern) {
    Matcher matcher = Pattern.compile(pattern).matcher(text);
    assertTrue(matcher.find(), "未找到版本号：" + pattern);
    return matcher.group(1).trim();
  }
}
