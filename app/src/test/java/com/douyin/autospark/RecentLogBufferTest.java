package awa.uxu.douyin.autospark;

import org.junit.jupiter.api.Test;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecentLogBufferTest {
  @Test
  void retainsRecentLinesAndTrimsInBatches() throws Exception {
    JTextArea area = new JTextArea();
    SwingUtilities.invokeAndWait(() -> {
      for (int index = 1; index <= 1101; index++) {
        RecentLogBuffer.appendLine(area, "message-" + index);
      }
    });

    List<String> lines = area.getText().lines().toList();
    assertEquals(RecentLogBuffer.MAX_VISIBLE_LINES, lines.size());
    assertEquals("message-102", lines.getFirst());
    assertEquals("message-1101", lines.getLast());
  }

  @Test
  void doesNotDiscardLinesBeforeTheCapacityIsReached() throws Exception {
    JTextArea area = new JTextArea();
    SwingUtilities.invokeAndWait(() -> {
      for (int index = 1; index <= RecentLogBuffer.MAX_VISIBLE_LINES; index++) {
        RecentLogBuffer.appendLine(area, "message-" + index);
      }
    });

    List<String> lines = area.getText().lines().toList();
    assertEquals(RecentLogBuffer.MAX_VISIBLE_LINES, lines.size());
    assertEquals("message-1", lines.getFirst());
    assertEquals("message-" + RecentLogBuffer.MAX_VISIBLE_LINES, lines.getLast());
  }
}
