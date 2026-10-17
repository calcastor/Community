package dev.pgm.community.requests.services;

import dev.pgm.community.requests.audit.CurrencyHistoryEntry;

public interface CurrencyAuditQuery {

  String TABLE_NAME = "request_currency_history";
  String TABLE_FIELDS = "(event_id VARCHAR(36) PRIMARY KEY, player_id VARCHAR(36) NOT NULL, "
      + "currency VARCHAR(20) NOT NULL, occurred_at BIGINT NOT NULL, action VARCHAR(32) NOT NULL, "
      + "requested_amount INT NOT NULL, actual_change INT NOT NULL, balance_before INT NOT NULL, "
      + "balance_after INT NOT NULL, actor_id VARCHAR(36), actor_name VARCHAR(255) NOT NULL, "
      + "context VARCHAR(2048), related_event_id VARCHAR(36), server_id VARCHAR(255) NOT NULL)";

  String REFUND_TABLE_NAME = "request_currency_refunds";
  String REFUND_TABLE_FIELDS = "(event_id VARCHAR(36) PRIMARY KEY, player_id VARCHAR(36) NOT NULL, "
      + "currency VARCHAR(20) NOT NULL, context VARCHAR(2048), related_event_id VARCHAR(36))";

  int HISTORY_PAGE_SIZE = CurrencyHistoryEntry.PAGE_SIZE;
  int REFUND_BATCH_SIZE = 100;

  String SELECT_EVENT_QUERY =
      "SELECT player_id, currency FROM " + TABLE_NAME + " WHERE event_id = ?";
  String COUNT_HISTORY_QUERY =
      "SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE player_id = ? AND currency = ?";
  String SELECT_HISTORY_QUERY =
      "SELECT * FROM " + TABLE_NAME + " WHERE player_id = ? AND currency = ? "
          + "ORDER BY occurred_at DESC, event_id DESC LIMIT ? OFFSET ?";
  String INSERT_HISTORY_QUERY = "INSERT INTO " + TABLE_NAME
      + " (event_id, player_id, currency, occurred_at, action, requested_amount, actual_change, "
      + "balance_before, balance_after, actor_id, actor_name, context, related_event_id, server_id) "
      + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

  String SELECT_REFUND_QUERY = "SELECT event_id FROM " + REFUND_TABLE_NAME + " WHERE event_id = ?";
  String SELECT_PENDING_REFUNDS_QUERY =
      "SELECT * FROM " + REFUND_TABLE_NAME + " LIMIT " + REFUND_BATCH_SIZE;
  String INSERT_REFUND_QUERY = "INSERT INTO " + REFUND_TABLE_NAME
      + " (event_id, player_id, currency, context, related_event_id) VALUES (?,?,?,?,?)";
  String DELETE_REFUND_QUERY = "DELETE FROM " + REFUND_TABLE_NAME + " WHERE event_id = ?";

  String SELECT_PROFILE_QUERY = "SELECT * FROM " + RequestQuery.TABLE_NAME + " WHERE id = ?";
}
