package awa.uxu.douyin.autospark;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public class AccountProfile {
  private String id = "";
  private String name = "";
  private LocalDateTime createdAt = LocalDateTime.now();

  public static AccountProfile create(String name) {
    AccountProfile profile = new AccountProfile();
    profile.id = UUID.randomUUID().toString();
    profile.name = normalizeName(name);
    return profile;
  }

  public void normalize() {
    id = requireValidId(id);
    name = normalizeName(name);
    if (createdAt == null) {
      createdAt = LocalDateTime.now();
    }
  }

  public static String requireValidId(String value) {
    String id = Objects.requireNonNullElse(value, "").trim().toLowerCase();
    if (!id.matches("[a-z0-9][a-z0-9-]{0,63}")) {
      throw new IllegalArgumentException("账号标识格式无效");
    }
    return id;
  }

  public static String normalizeName(String value) {
    String name = Objects.requireNonNullElse(value, "").trim();
    if (name.isBlank()) {
      throw new IllegalArgumentException("账号名称不能为空");
    }
    if (name.length() > 30) {
      throw new IllegalArgumentException("账号名称不能超过 30 个字符");
    }
    return name;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  @Override
  public String toString() {
    return name;
  }
}
