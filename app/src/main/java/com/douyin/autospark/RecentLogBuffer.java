package awa.uxu.douyin.autospark;

import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;

/** Keeps the live log view responsive during long-running sessions. Call on the EDT. */
final class RecentLogBuffer {
  static final int MAX_VISIBLE_LINES = 1000;
  private static final int TRIM_BATCH_LINES = 100;
  private static final int RETAIN_AFTER_TRIM = MAX_VISIBLE_LINES - TRIM_BATCH_LINES;

  private RecentLogBuffer() {}

  static void appendLine(JTextArea area, String line) {
    if (area.getDocument().getLength() > 0) {
      area.append(System.lineSeparator());
    }
    area.append(line);
    trimIfNeeded(area);
  }

  private static void trimIfNeeded(JTextArea area) {
    int lineCount = area.getLineCount();
    if (lineCount <= MAX_VISIBLE_LINES) {
      return;
    }

    int linesToRemove = lineCount - RETAIN_AFTER_TRIM;
    try {
      int endOffset = area.getLineStartOffset(linesToRemove);
      area.getDocument().remove(0, endOffset);
    } catch (BadLocationException error) {
      throw new IllegalStateException("裁剪实时日志失败", error);
    }
  }
}
