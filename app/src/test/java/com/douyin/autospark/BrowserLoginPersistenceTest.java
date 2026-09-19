package awa.uxu.douyin.autospark;

import com.microsoft.playwright.options.Cookie;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserLoginPersistenceTest {
  @Test
  void promotesOnlyDouyinSessionCookiesWithoutChangingTheirSecurityAttributes() {
    Instant now = Instant.parse("2026-08-21T12:00:00Z");
    Cookie sessionCookie = new Cookie("sessionid", "登录令牌")
        .setDomain(".douyin.com")
        .setPath("/")
        .setHttpOnly(true)
        .setSecure(true);
    Cookie existingPersistentCookie = new Cookie("persistent", "保留原值")
        .setDomain("creator.douyin.com")
        .setPath("/")
        .setExpires(now.plus(Duration.ofDays(7)).getEpochSecond());
    Cookie unrelatedCookie = new Cookie("other", "不应处理")
        .setDomain("example.com")
        .setPath("/");

    List<Cookie> result = BrowserLoginPersistence.createDurableDouyinCookies(
        List.of(sessionCookie, existingPersistentCookie, unrelatedCookie), now);

    assertEquals(1, result.size());
    Cookie durable = result.getFirst();
    assertEquals("sessionid", durable.name);
    assertEquals("登录令牌", durable.value);
    assertEquals(".douyin.com", durable.domain);
    assertEquals("/", durable.path);
    assertEquals(Boolean.TRUE, durable.httpOnly);
    assertEquals(Boolean.TRUE, durable.secure);
    assertTrue(durable.expires >= now.plus(Duration.ofDays(179)).getEpochSecond());
  }
}
