package dev.pgm.community.requests.services;

import dev.pgm.community.database.DatabaseExecutor;
import dev.pgm.community.database.Query;
import dev.pgm.community.database.dialect.SqlDialect;
import dev.pgm.community.requests.RequestProfile;
import dev.pgm.community.requests.audit.Currency;
import dev.pgm.community.requests.audit.CurrencyAction;
import dev.pgm.community.requests.audit.CurrencyChange;
import dev.pgm.community.requests.audit.CurrencyHistoryEntry;
import dev.pgm.community.requests.audit.CurrencyHistoryPage;
import dev.pgm.community.requests.audit.CurrencyResult;
import dev.pgm.community.requests.audit.InsufficientCurrencyException;
import dev.pgm.community.requests.audit.PendingRefund;
import dev.pgm.community.requests.audit.RefreshNotEligibleException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SQLCurrencyAuditService implements CurrencyAuditQuery {
  private final SqlDialect dialect;
  private CompletableFuture<Void> ready;

  public SQLCurrencyAuditService(SqlDialect dialect) {
    this.dialect = dialect;
  }

  public synchronized CompletableFuture<Void> initialize() {
    if (ready == null || ready.isCompletedExceptionally()) {
      ready = DatabaseExecutor.transactionAsync(connection -> {
            createTables(connection);
            requireTransactionalTables(connection);
            return null;
          })
          .thenCompose(ignored -> DatabaseExecutor.createIndexAsync(
              TABLE_NAME,
              "request_currency_player_history",
              "player_id, currency, occurred_at, event_id"))
          .thenApply(ignored -> null);
    }
    return ready;
  }

  public CompletableFuture<CurrencyResult> change(
      RequestProfile profile, CurrencyChange change, int maxTokens, String serverId) {
    return initialize()
        .thenCompose(ignored -> DatabaseExecutor.transactionAsync(connection -> applyChange(
                connection,
                profile.getPlayerId(),
                change,
                maxTokens,
                System.currentTimeMillis(),
                serverId))
            .thenApply(result -> {
              profile.applyCurrency(
                  change.currency(),
                  result.balance(),
                  instant(result.lastTokenRefresh()),
                  instant(result.lastSpend()),
                  result.lastSponsorMap());
              return new CurrencyResult(result.before(), result.balance(), result.duplicate());
            }));
  }

  public CompletableFuture<Void> queueRefund(UUID playerId, CurrencyChange change) {
    return initialize()
        .thenCompose(ignored -> DatabaseExecutor.transactionAsync(connection -> {
          try (PreparedStatement existing =
                  prepare(connection, SELECT_REFUND_QUERY, change.eventId().toString());
              ResultSet rows = existing.executeQuery()) {
            if (rows.next()) return null;
          }
          try (PreparedStatement statement = prepare(
              connection,
              INSERT_REFUND_QUERY,
              change.eventId().toString(),
              playerId.toString(),
              change.currency().name(),
              change.context(),
              change.relatedEventId() == null ? null : change.relatedEventId().toString())) {
            statement.executeUpdate();
          }
          return null;
        }));
  }

  public CompletableFuture<List<PendingRefund>> getPendingRefunds() {
    return DatabaseExecutor.transactionAsync(connection -> {
      List<PendingRefund> refunds = new ArrayList<>();
      try (ResultSet tables =
          connection.getMetaData().getTables(null, null, REFUND_TABLE_NAME, null)) {
        if (!tables.next()) return refunds;
      }
      try (PreparedStatement statement = prepare(connection, SELECT_PENDING_REFUNDS_QUERY);
          ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          String related = rows.getString("related_event_id");
          refunds.add(new PendingRefund(
              UUID.fromString(rows.getString("player_id")),
              new CurrencyChange(
                  UUID.fromString(rows.getString("event_id")),
                  Currency.valueOf(rows.getString("currency")),
                  1,
                  CurrencyAction.REFUND,
                  null,
                  "SYSTEM",
                  rows.getString("context"),
                  related == null ? null : UUID.fromString(related))));
        }
      }
      return refunds;
    });
  }

  public CompletableFuture<CurrencyHistoryPage> getHistory(
      UUID playerId, Currency currency, int page) {
    // Read-only: disabling auditing neither deletes history nor creates new schema.
    return DatabaseExecutor.transactionAsync(
        connection -> readHistory(connection, playerId, currency, page));
  }

  private static CurrencyHistoryPage readHistory(
      Connection connection, UUID playerId, Currency currency, int page) throws SQLException {
    if (page < 1) throw new IllegalArgumentException("Page must be positive");
    List<CurrencyHistoryEntry> entries = new ArrayList<>();
    // Auditing may never have been enabled, in which case there is simply no history
    try (ResultSet tables = connection.getMetaData().getTables(null, null, TABLE_NAME, null)) {
      if (!tables.next()) return new CurrencyHistoryPage(entries, 0);
    }
    int total;
    try (PreparedStatement statement =
            prepare(connection, COUNT_HISTORY_QUERY, playerId.toString(), currency.name());
        ResultSet rows = statement.executeQuery()) {
      total = rows.next() ? rows.getInt(1) : 0;
    }
    try (PreparedStatement statement = prepare(
            connection,
            SELECT_HISTORY_QUERY,
            playerId.toString(),
            currency.name(),
            HISTORY_PAGE_SIZE,
            (long) (page - 1) * HISTORY_PAGE_SIZE);
        ResultSet rows = statement.executeQuery()) {
      while (rows.next())
        entries.add(new CurrencyHistoryEntry(
            UUID.fromString(rows.getString("event_id")),
            Instant.ofEpochMilli(rows.getLong("occurred_at")),
            CurrencyAction.valueOf(rows.getString("action")),
            rows.getInt("requested_amount"),
            rows.getInt("actual_change"),
            rows.getInt("balance_before"),
            rows.getInt("balance_after"),
            rows.getString("actor_name"),
            rows.getString("context"),
            rows.getString("server_id")));
    }
    return new CurrencyHistoryPage(entries, total);
  }

  private void createTables(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          Query.createTable(TABLE_NAME, TABLE_FIELDS) + dialect.transactionalTableOptions());
      statement.executeUpdate(Query.createTable(REFUND_TABLE_NAME, REFUND_TABLE_FIELDS)
          + dialect.transactionalTableOptions());
    }
  }

  private CurrencyState applyChange(
      Connection connection,
      UUID playerId,
      CurrencyChange change,
      int maxTokens,
      long now,
      String serverId)
      throws SQLException {
    // A stable event ID makes refund retries safe even after an ambiguous commit result.
    try (PreparedStatement statement =
            prepare(connection, SELECT_EVENT_QUERY, change.eventId().toString());
        ResultSet rows = statement.executeQuery()) {
      if (rows.next()) {
        if (!playerId.toString().equals(rows.getString("player_id"))
            || !change.currency().name().equals(rows.getString("currency"))) {
          throw new SQLException("Currency event belongs to a different player or currency");
        }
        try (PreparedStatement cleanup =
            prepare(connection, DELETE_REFUND_QUERY, change.eventId().toString())) {
          cleanup.executeUpdate();
        }
        // Already applied, report the current balance without recording anything new
        return readCurrencyState(connection, playerId, change.currency());
      }
    }
    String column = balanceColumn(change.currency());
    int before;
    long refresh;
    long spent;
    String map;
    try (PreparedStatement statement = prepare(
            connection,
            SELECT_PROFILE_QUERY + dialect.selectForUpdateClause(),
            playerId.toString());
        ResultSet rows = statement.executeQuery()) {
      if (!rows.next()) throw new SQLException("Request profile not found for " + playerId);
      before = rows.getInt(column);
      refresh = rows.getLong("last_token_refresh");
      spent = rows.getLong(
          change.currency() == Currency.SPONSOR_TOKEN ? "last_sponsor_time" : "last_super_vote");
      map = rows.getString("last_sponsor_map");
    }
    boolean refreshAction = change.action().isTokenRefresh();
    if (refreshAction) {
      // Preserve RequestProfile's elapsed whole-hour/day eligibility rules.
      long interval = (change.action() == CurrencyAction.DAILY_REFRESH
              ? Duration.ofHours(25)
              : Duration.ofDays(8))
          .toMillis();
      if (before >= maxTokens || (refresh != -1 && now - refresh < interval)) {
        throw new RefreshNotEligibleException();
      }
    }
    if (change.action() == CurrencyAction.SPEND && before < -change.amount()) {
      throw new InsufficientCurrencyException();
    }
    long originalRefresh = refresh;
    long calculated = Math.max(0L, (long) before + change.amount());
    if (calculated > Integer.MAX_VALUE) throw new SQLException("Balance exceeds supported range");
    int after = (int) calculated;
    String extra = "";
    List<Object> params = new ArrayList<>();
    params.add(after);
    if (refreshAction) {
      refresh = now;
      extra = ", last_token_refresh = ?";
      params.add(now);
    } else if (change.action() == CurrencyAction.SPEND) {
      spent = now;
      extra = change.currency() == Currency.SPONSOR_TOKEN
          ? ", last_sponsor_time = ?, last_sponsor_map = ?"
          : ", last_super_vote = ?";
      params.add(now);
      if (change.currency() == Currency.SPONSOR_TOKEN) {
        map = change.context();
        params.add(map);
      }
    }
    params.add(playerId.toString());
    params.add(before);
    if (refreshAction) params.add(originalRefresh);
    try (PreparedStatement statement = prepare(
        connection,
        "UPDATE " + RequestQuery.TABLE_NAME + " SET " + column + " = ?" + extra
            + " WHERE id = ? AND " + column + " = ?"
            + (refreshAction ? " AND last_token_refresh = ?" : ""),
        params.toArray())) {
      int updated = statement.executeUpdate();
      // MySQL can report zero affected rows for a no-op adjustment. The row is already locked.
      boolean unchangedBalance =
          !dialect.reportsMatchedRows() && before == after && extra.isEmpty();
      if (updated != 1 && !unchangedBalance)
        throw new SQLException("Concurrent balance change; please retry");
    }
    try (PreparedStatement statement = prepare(
        connection,
        INSERT_HISTORY_QUERY,
        change.eventId().toString(),
        playerId.toString(),
        change.currency().name(),
        now,
        change.action().name(),
        change.amount(),
        after - before,
        before,
        after,
        change.actorId() == null ? null : change.actorId().toString(),
        change.actorName(),
        change.context(),
        change.relatedEventId() == null ? null : change.relatedEventId().toString(),
        serverId)) {
      statement.executeUpdate();
    }
    if (change.action() == CurrencyAction.REFUND) {
      try (PreparedStatement statement =
          prepare(connection, DELETE_REFUND_QUERY, change.eventId().toString())) {
        statement.executeUpdate();
      }
    }
    // Cache publication happens after commit, never while the transaction can still roll
    // back.
    return new CurrencyState(before, after, refresh, spent, map, false);
  }

  private static String balanceColumn(Currency currency) {
    return currency == Currency.SPONSOR_TOKEN ? "tokens" : "super_votes";
  }

  private void requireTransactionalTables(Connection connection) throws SQLException {
    String query = dialect.findNonTransactionalTableQuery();
    if (query == null) return;
    for (String table : List.of(RequestQuery.TABLE_NAME, TABLE_NAME, REFUND_TABLE_NAME)) {
      try (PreparedStatement statement = prepare(connection, query, table);
          ResultSet rows = statement.executeQuery()) {
        if (rows.next())
          throw new SQLException("Currency auditing requires transactional tables: " + table);
      }
    }
  }

  private CurrencyState readCurrencyState(Connection connection, UUID playerId, Currency currency)
      throws SQLException {
    try (PreparedStatement statement = prepare(
            connection,
            SELECT_PROFILE_QUERY + dialect.selectForUpdateClause(),
            playerId.toString());
        ResultSet rows = statement.executeQuery()) {
      if (!rows.next()) throw new SQLException("Player profile not found");
      int balance = rows.getInt(balanceColumn(currency));
      return new CurrencyState(
          balance,
          balance,
          rows.getLong("last_token_refresh"),
          rows.getLong(
              currency == Currency.SPONSOR_TOKEN ? "last_sponsor_time" : "last_super_vote"),
          rows.getString("last_sponsor_map"),
          true);
    }
  }

  private record CurrencyState(
      int before,
      int balance,
      long lastTokenRefresh,
      long lastSpend,
      String lastSponsorMap,
      boolean duplicate) {}

  private static Instant instant(long time) {
    return time == -1 ? null : Instant.ofEpochMilli(time);
  }

  private static PreparedStatement prepare(Connection connection, String sql, Object... params)
      throws SQLException {
    PreparedStatement statement = connection.prepareStatement(sql);
    try {
      for (int i = 0; i < params.length; i++) statement.setObject(i + 1, params[i]);
      return statement;
    } catch (SQLException failure) {
      statement.close();
      throw failure;
    }
  }
}
