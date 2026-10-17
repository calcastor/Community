package dev.pgm.community.database.dialect;

public interface SqlDialect {

  String upsertLatestSessionQuery();

  String upsertUserSettingQuery();

  String createIndexQuery(String table, String indexName, String columns);

  String findIndexQuery();

  /** Options for new tables that participate in multi-statement transactions. */
  default String transactionalTableOptions() {
    return "";
  }

  default String selectForUpdateClause() {
    return "";
  }

  /** Returns a table's name if its engine cannot support the transaction contract. */
  default String findNonTransactionalTableQuery() {
    return null;
  }

  default boolean reportsMatchedRows() {
    return true;
  }
}
