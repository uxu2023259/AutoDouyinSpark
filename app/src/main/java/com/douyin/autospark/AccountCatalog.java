package com.douyin.autospark;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AccountCatalog {
  private int schemaVersion = 1;
  private boolean startWithWindows = true;
  private String selectedAccountId = "";
  private List<AccountProfile> accounts = new ArrayList<>();

  public void normalize() {
    schemaVersion = Math.max(1, schemaVersion);
    if (accounts == null) {
      accounts = new ArrayList<>();
    }
    Set<String> ids = new HashSet<>();
    Set<String> names = new HashSet<>();
    for (AccountProfile account : accounts) {
      account.normalize();
      if (!ids.add(account.getId())) {
        throw new IllegalStateException("账号标识重复：" + account.getId());
      }
      if (!names.add(account.getName().toLowerCase())) {
        throw new IllegalStateException("账号名称重复：" + account.getName());
      }
    }
    if (accounts.isEmpty()) {
      selectedAccountId = "";
    } else if (accounts.stream().noneMatch(account -> account.getId().equals(selectedAccountId))) {
      selectedAccountId = accounts.getFirst().getId();
    }
  }

  public int getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(int schemaVersion) {
    this.schemaVersion = schemaVersion;
  }

  public boolean isStartWithWindows() {
    return startWithWindows;
  }

  public void setStartWithWindows(boolean startWithWindows) {
    this.startWithWindows = startWithWindows;
  }

  public String getSelectedAccountId() {
    return selectedAccountId;
  }

  public void setSelectedAccountId(String selectedAccountId) {
    this.selectedAccountId = selectedAccountId == null ? "" : selectedAccountId;
  }

  public List<AccountProfile> getAccounts() {
    return accounts;
  }

  public void setAccounts(List<AccountProfile> accounts) {
    this.accounts = accounts;
  }
}
