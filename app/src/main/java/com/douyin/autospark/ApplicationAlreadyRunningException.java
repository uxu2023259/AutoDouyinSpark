package awa.uxu.douyin.autospark;

public class ApplicationAlreadyRunningException extends IllegalStateException {
  public ApplicationAlreadyRunningException(String message) {
    super(message);
  }
}
