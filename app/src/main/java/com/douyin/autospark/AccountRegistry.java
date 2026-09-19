package awa.uxu.douyin.autospark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AccountRegistry {
  private static final String DEFAULT_ACCOUNT_ID = "default";
  private static final DateTimeFormatter ARCHIVE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  private final AppPaths paths;
  private final ObjectMapper mapper;
  private AccountCatalog catalog;

  public AccountRegistry(AppPaths paths) {
    this.paths = paths;
    this.mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .enable(SerializationFeature.INDENT_OUTPUT)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  public synchronized void initialize() throws IOException {
    paths.ensureDirectories();
    if (Files.exists(paths.accountsPath())) {
      catalog = mapper.readValue(Files.readString(paths.accountsPath(), StandardCharsets.UTF_8), AccountCatalog.class);
      catalog.normalize();
      save();
      return;
    }

    catalog = new AccountCatalog();
    AccountProfile defaultAccount = AccountProfile.create("默认账号");
    defaultAccount.setId(DEFAULT_ACCOUNT_ID);
    catalog.setAccounts(new ArrayList<>(List.of(defaultAccount)));
    catalog.setSelectedAccountId(DEFAULT_ACCOUNT_ID);
    migrateLegacyData(defaultAccount);
    catalog.normalize();
    save();
  }

  public synchronized List<AccountProfile> accounts() {
    ensureInitialized();
    return List.copyOf(catalog.getAccounts());
  }

  public synchronized AccountProfile create(String name) throws IOException {
    ensureInitialized();
    String normalizedName = AccountProfile.normalizeName(name);
    ensureUniqueName(normalizedName, null);
    AccountProfile account = AccountProfile.create(normalizedName);
    catalog.getAccounts().add(account);
    catalog.setSelectedAccountId(account.getId());
    new ConfigStore(paths.account(account.getId())).saveConfig(AppConfig.defaults());
    save();
    return account;
  }

  public synchronized void rename(String accountId, String name) throws IOException {
    AccountProfile account = findRequired(accountId);
    String normalizedName = AccountProfile.normalizeName(name);
    ensureUniqueName(normalizedName, accountId);
    account.setName(normalizedName);
    save();
  }

  public synchronized Path archive(String accountId) throws IOException {
    ensureInitialized();
    if (catalog.getAccounts().size() <= 1) {
      throw new IllegalStateException("至少需要保留一个账号");
    }
    AccountProfile account = findRequired(accountId);
    Path source = paths.account(accountId).accountDir();
    Path archiveRoot = paths.appDir().resolve("archived-accounts");
    Files.createDirectories(archiveRoot);
    Path destination = archiveRoot.resolve(ARCHIVE_TIME.format(LocalDateTime.now()) + "-" + accountId);
    int accountIndex = catalog.getAccounts().indexOf(account);
    String previousSelectedAccountId = catalog.getSelectedAccountId();
    if (Files.exists(source)) {
      moveWithFallback(source, destination);
    }
    catalog.getAccounts().removeIf(item -> item.getId().equals(accountId));
    if (accountId.equals(catalog.getSelectedAccountId())) {
      catalog.setSelectedAccountId(catalog.getAccounts().getFirst().getId());
    }
    try {
      save();
    } catch (IOException | RuntimeException error) {
      catalog.getAccounts().add(accountIndex, account);
      catalog.setSelectedAccountId(previousSelectedAccountId);
      if (Files.exists(destination) && !Files.exists(source)) {
        moveWithFallback(destination, source);
      }
      throw error;
    }
    return destination;
  }

  public synchronized String selectedAccountId() {
    ensureInitialized();
    return catalog.getSelectedAccountId();
  }

  public synchronized void select(String accountId) throws IOException {
    findRequired(accountId);
    catalog.setSelectedAccountId(accountId);
    save();
  }

  public synchronized boolean isStartWithWindows() {
    ensureInitialized();
    return catalog.isStartWithWindows();
  }

  public synchronized void setStartWithWindows(boolean enabled) throws IOException {
    ensureInitialized();
    catalog.setStartWithWindows(enabled);
    save();
  }

  private void migrateLegacyData(AccountProfile account) throws IOException {
    AccountPaths accountPaths = paths.account(account.getId());
    accountPaths.ensureDirectories();
    moveIfPresent(paths.legacyConfigPath(), accountPaths.configPath());
    moveIfPresent(paths.legacyStatePath(), accountPaths.statePath());
    moveDirectoryIfPresent(paths.legacyBrowserProfileDir(), accountPaths.browserProfileDir());
    moveDirectoryIfPresent(paths.legacyScreenshotDir(), accountPaths.screenshotDir());
    ConfigStore store = new ConfigStore(accountPaths);
    AppConfig config = store.loadConfig();
    catalog.setStartWithWindows(config.isStartWithWindows());
  }

  private void moveIfPresent(Path source, Path destination) throws IOException {
    if (Files.exists(source) && !Files.exists(destination)) {
      moveWithFallback(source, destination);
    }
  }

  private void moveDirectoryIfPresent(Path source, Path destination) throws IOException {
    if (!Files.exists(source)) {
      return;
    }
    if (Files.isDirectory(destination) && isDirectoryEmpty(destination)) {
      Files.delete(destination);
    }
    if (!Files.exists(destination)) {
      moveWithFallback(source, destination);
    }
  }

  private void moveWithFallback(Path source, Path destination) throws IOException {
    try {
      Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
    } catch (java.nio.file.AtomicMoveNotSupportedException error) {
      Files.move(source, destination);
    }
  }

  private boolean isDirectoryEmpty(Path directory) throws IOException {
    try (var stream = Files.list(directory)) {
      return stream.findAny().isEmpty();
    }
  }

  private AccountProfile findRequired(String accountId) {
    ensureInitialized();
    return catalog.getAccounts().stream()
        .filter(account -> account.getId().equals(accountId))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("账号不存在：" + accountId));
  }

  private void ensureUniqueName(String name, String excludedId) {
    boolean duplicate = catalog.getAccounts().stream()
        .anyMatch(account -> !account.getId().equals(excludedId) && account.getName().equalsIgnoreCase(name));
    if (duplicate) {
      throw new IllegalArgumentException("账号名称已存在：" + name);
    }
  }

  private void save() throws IOException {
    catalog.normalize();
    String content = mapper.writeValueAsString(catalog) + System.lineSeparator();
    Path temporary = paths.accountsPath().resolveSibling("accounts.json.tmp");
    Files.writeString(temporary, content, StandardCharsets.UTF_8);
    try {
      Files.move(temporary, paths.accountsPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (java.nio.file.AtomicMoveNotSupportedException error) {
      Files.move(temporary, paths.accountsPath(), StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private void ensureInitialized() {
    if (catalog == null) {
      throw new IllegalStateException("账号注册中心尚未初始化");
    }
  }
}
