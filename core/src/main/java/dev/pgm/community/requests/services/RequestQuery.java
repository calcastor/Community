package dev.pgm.community.requests.services;

public interface RequestQuery {

  String TABLE_FIELDS =
      "(id VARCHAR(36) PRIMARY KEY, last_request_time LONG, last_request_map VARCHAR(255), last_sponsor_time LONG, last_sponsor_map VARCHAR(255), tokens INT, last_token_refresh LONG, super_votes INT, last_super_vote LONG)";
  String TABLE_NAME = "requests";

  String INSERT_REQUESTS_QUERY = "INSERT INTO " + TABLE_NAME + " VALUES (?,?,?,?,?,?,?,?,?)";

  String REQUEST_UPDATE_FIELDS = "last_request_time = ?, last_request_map = ?";
  String SPONSOR_UPDATE_FIELDS =
      "last_sponsor_time = ?, last_sponsor_map = ?, tokens = ?, last_token_refresh = ?";
  String SUPER_VOTE_UPDATE_FIELDS = "super_votes = ?, last_super_vote = ?";

  String UPDATE_REQUEST_QUERY = "UPDATE " + TABLE_NAME + " SET " + REQUEST_UPDATE_FIELDS + ", "
      + SPONSOR_UPDATE_FIELDS + ", " + SUPER_VOTE_UPDATE_FIELDS + " WHERE id = ?";

  String SELECT_REQUEST_QUERY = "SELECT * from " + TABLE_NAME + " WHERE id = ? LIMIT 1";
}
