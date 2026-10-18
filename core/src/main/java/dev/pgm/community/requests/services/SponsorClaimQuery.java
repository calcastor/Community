package dev.pgm.community.requests.services;

public interface SponsorClaimQuery {

  String CLAIM_TABLE_NAME = "request_sponsor_claims";
  String CLAIM_TABLE_FIELDS =
      "(player_id VARCHAR(36) PRIMARY KEY, server_id VARCHAR(255) NOT NULL, "
          + "map_id VARCHAR(255), claimed_at BIGINT NOT NULL)";

  String SELECT_CLAIM_QUERY = "SELECT server_id FROM " + CLAIM_TABLE_NAME + " WHERE player_id = ?";
  String INSERT_CLAIM_QUERY = "INSERT INTO " + CLAIM_TABLE_NAME
      + " (player_id, server_id, map_id, claimed_at) VALUES (?,?,?,?)";
  String DELETE_CLAIM_QUERY = "DELETE FROM " + CLAIM_TABLE_NAME + " WHERE player_id = ?";
  String RELEASE_CLAIM_QUERY =
      "DELETE FROM " + CLAIM_TABLE_NAME + " WHERE player_id = ? AND server_id = ?";
  String RELEASE_SERVER_CLAIMS_QUERY = "DELETE FROM " + CLAIM_TABLE_NAME + " WHERE server_id = ?";
}
