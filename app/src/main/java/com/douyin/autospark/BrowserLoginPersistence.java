package com.douyin.autospark;

import com.microsoft.playwright.options.Cookie;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class BrowserLoginPersistence {
  private static final Duration SESSION_COOKIE_LIFETIME = Duration.ofDays(180);

  private BrowserLoginPersistence() {
  }

  static List<Cookie> createDurableDouyinCookies(List<Cookie> cookies, Instant now) {
    double expiresAt = now.plus(SESSION_COOKIE_LIFETIME).getEpochSecond();
    List<Cookie> durableCookies = new ArrayList<>();
    for (Cookie cookie : cookies) {
      if (isDouyinSessionCookie(cookie)) {
        durableCookies.add(copyWithExpiry(cookie, expiresAt));
      }
    }
    return durableCookies;
  }

  private static boolean isDouyinSessionCookie(Cookie cookie) {
    if (cookie == null || cookie.name == null || cookie.value == null) {
      return false;
    }
    String domain = cookie.domain == null ? "" : cookie.domain.toLowerCase(Locale.ROOT);
    boolean douyinDomain = domain.equals("douyin.com") || domain.endsWith(".douyin.com");
    return douyinDomain && (cookie.expires == null || cookie.expires <= 0);
  }

  private static Cookie copyWithExpiry(Cookie source, double expiresAt) {
    Cookie copy = new Cookie(source.name, source.value);
    if (source.url != null && !source.url.isBlank()) {
      copy.setUrl(source.url);
    } else {
      copy.setDomain(source.domain);
      copy.setPath(source.path == null || source.path.isBlank() ? "/" : source.path);
    }
    copy.setExpires(expiresAt);
    if (source.httpOnly != null) {
      copy.setHttpOnly(source.httpOnly);
    }
    if (source.secure != null) {
      copy.setSecure(source.secure);
    }
    if (source.sameSite != null) {
      copy.setSameSite(source.sameSite);
    }
    if (source.partitionKey != null && !source.partitionKey.isBlank()) {
      copy.setPartitionKey(source.partitionKey);
    }
    return copy;
  }
}
