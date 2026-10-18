package dev.pgm.community.requests.services;

import dev.pgm.community.database.DatabaseExecutor;
import dev.pgm.community.database.Query;
import dev.pgm.community.database.dialect.SqlDialect;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SQLSponsorClaimService implements SponsorClaimQuery {
  private final SqlDialect dialect;
  private CompletableFuture<Void> ready;

  public SQLSponsorClaimService(SqlDialect dialect) {
    this.dialect = dialect;
  }

  private synchronized CompletableFuture<Void> initialize() {
    if (ready == null || ready.isCompletedExceptionally()) {
      ready = DatabaseExecutor.transactionAsync(connection -> {
        try (Statement statement = connection.createStatement()) {
          statement.executeUpdate(Query.createTable(CLAIM_TABLE_NAME, CLAIM_TABLE_FIELDS)
              + dialect.transactionalTableOptions());
        }
        return null;
      });
    }
    return ready;
  }

  public CompletableFuture<Optional<String>> claim(UUID playerId, String serverId, String mapId) {
    return initialize()
        .thenCompose(ignored -> DatabaseExecutor.transactionAsync(connection -> {
          Optional<String> previous = selectOwner(connection, playerId, true);
          try (PreparedStatement statement = connection.prepareStatement(DELETE_CLAIM_QUERY)) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
          }
          try (PreparedStatement statement = connection.prepareStatement(INSERT_CLAIM_QUERY)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, serverId);
            statement.setString(3, mapId);
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
          }
          return previous.filter(server -> !server.equalsIgnoreCase(serverId));
        }));
  }

  public CompletableFuture<Boolean> owns(UUID playerId, String serverId) {
    return initialize()
        .thenCompose(ignored ->
            DatabaseExecutor.transactionAsync(connection -> selectOwner(connection, playerId, false)
                .map(server -> server.equalsIgnoreCase(serverId))
                .orElse(true)));
  }

  public CompletableFuture<Void> release(UUID playerId, String serverId) {
    return initialize()
        .thenCompose(ignored -> DatabaseExecutor.transactionAsync(connection -> {
          try (PreparedStatement statement = connection.prepareStatement(RELEASE_CLAIM_QUERY)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, serverId);
            statement.executeUpdate();
          }
          return null;
        }));
  }

  public CompletableFuture<Void> releaseAll(String serverId) {
    return initialize()
        .thenCompose(ignored -> DatabaseExecutor.transactionAsync(connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement(RELEASE_SERVER_CLAIMS_QUERY)) {
            statement.setString(1, serverId);
            statement.executeUpdate();
          }
          return null;
        }));
  }

  private Optional<String> selectOwner(Connection connection, UUID playerId, boolean lock)
      throws SQLException {
    String sql = SELECT_CLAIM_QUERY + (lock ? dialect.selectForUpdateClause() : "");
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, playerId.toString());
      try (ResultSet rows = statement.executeQuery()) {
        return rows.next() ? Optional.of(rows.getString("server_id")) : Optional.empty();
      }
    }
  }
}
