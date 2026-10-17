package dev.pgm.community.requests.store;

import dev.pgm.community.requests.RequestProfile;
import dev.pgm.community.requests.audit.Currency;
import dev.pgm.community.requests.audit.CurrencyChange;
import dev.pgm.community.requests.audit.CurrencyHistoryPage;
import dev.pgm.community.requests.audit.CurrencyResult;
import dev.pgm.community.requests.audit.PendingRefund;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface RequestStore {

  /** Loads a profile, optionally waiting for a new account to be persisted before returning it. */
  CompletableFuture<RequestProfile> login(UUID playerId, boolean awaitPersistence);

  /** Updates activity and the selected currency fields. */
  void update(RequestProfile profile, boolean updateSponsorTokens, boolean updateSuperVotes);

  /** Creates optional audit storage when requested by the feature. */
  CompletableFuture<Void> initializeCurrencyAudit();

  CompletableFuture<CurrencyResult> changeCurrency(
      RequestProfile profile, CurrencyChange change, int maxTokens, String serverId);

  CompletableFuture<Void> queueRefund(PendingRefund refund);

  CompletableFuture<List<PendingRefund>> getPendingRefunds();

  CompletableFuture<CurrencyHistoryPage> getCurrencyHistory(
      UUID playerId, Currency currency, int page);

  CompletableFuture<RequestProfile> query(String target);

  RequestProfile getCached(UUID playerId);
}
