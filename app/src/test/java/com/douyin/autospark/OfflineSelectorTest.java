package com.douyin.autospark;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineSelectorTest {
  @Test
  void savedDouyinPageContainsSelectorsUsedByAutomation() throws Exception {
    Path snapshot = Path.of("抖音创作者中心.html");
    String html = Files.readString(snapshot, StandardCharsets.UTF_8);

    assertTrue(html.contains("ReactVirtualized__Grid"));
    assertTrue(html.contains("box-header-name"));
    assertTrue(html.contains("contenteditable=\"true\""));
    assertTrue(html.contains(">发送<"));
  }
}
