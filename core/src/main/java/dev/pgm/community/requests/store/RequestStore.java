package dev.pgm.community.requests.store;

import dev.pgm.community.requests.RequestProfile;
import dev.pgm.community.requests.audit.Currency;
import dev.pgm.community.requests.audit.CurrencyChange;
import dev.pgm.community.requests.audit.CurrencyHistoryPage;
import dev.pgm.community.requests.audit.CurrencyResult;
import dev.pgm.community.requests.audit.PendingRefund;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface RequestStore {

  /** Loads a profile, optionally waiting for a new account to be persisted before returning it. */
  CompletableFuture<RequestProfile> login(UUID playerId, boolean awaitPersistence);

  /** Updates activity and the selected currency fields. */
  CompletableFuture<Void> update(
      RequestProfile profile, boolean updateSponsorTokens, boolean updateSuperVotes);

  /** Drops the cached profile, so it is reloaded from the database on next use. */
  void invalidate(UUID playerId);

  /** Reloads the cached profile from the database in place, without leaving a gap. */
  CompletableFuture<RequestProfile> refresh(UUID playerId);

  /** Creates optional audit storage when requested by the feature. */
  CompletableFuture<Void> initializeCurrencyAudit();

  CompletableFuture<CurrencyResult> changeCurrency(
      RequestProfile profile, CurrencyChange change, int maxTokens, String serverId);

  CompletableFuture<Void> queueRefund(PendingRefund refund);

  CompletableFuture<List<PendingRefund>> getPendingRefunds();

  CompletableFuture<CurrencyHistoryPage> getCurrencyHistory(
      UUID playerId, Currency currency, int page);

  /** Claims the player's network-wide sponsor slot, returning the server it replaced, if any. */
  CompletableFuture<Optional<String>> claimSponsor(UUID playerId, String serverId, String mapId);

  /** Whether this server still owns the player's sponsor slot. */
  CompletableFuture<Boolean> ownsSponsorClaim(UUID playerId, String serverId);

  /** Releases the player's sponsor slot if this server owns it. */
  CompletableFuture<Void> releaseSponsorClaim(UUID playerId, String serverId);

  /** Releases every sponsor slot owned by this server. */
  CompletableFuture<Void> releaseServerSponsorClaims(String serverId);

  CompletableFuture<RequestProfile> query(String target);

  RequestProfile getCached(UUID playerId);
}
