package dev.pgm.community.requests.store;

import dev.pgm.community.database.DatabaseExecutor;
import dev.pgm.community.requests.RequestProfile;
import dev.pgm.community.requests.audit.Currency;
import dev.pgm.community.requests.audit.CurrencyChange;
import dev.pgm.community.requests.audit.CurrencyHistoryPage;
import dev.pgm.community.requests.audit.CurrencyResult;
import dev.pgm.community.requests.audit.PendingRefund;
import dev.pgm.community.requests.services.SQLCurrencyAuditService;
import dev.pgm.community.requests.services.SQLRequestService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SQLRequestStore implements RequestStore {

  private final SQLRequestService service;
  private final SQLCurrencyAuditService currencyAudit;

  public SQLRequestStore() {
    this.service = new SQLRequestService();
    this.currencyAudit = new SQLCurrencyAuditService(DatabaseExecutor.getDialect());
  }

  @Override
  public CompletableFuture<RequestProfile> login(UUID playerId, boolean awaitPersistence) {
    return service.login(playerId, awaitPersistence);
  }

  @Override
  public void update(
      RequestProfile profile, boolean updateSponsorTokens, boolean updateSuperVotes) {
    service.update(profile, updateSponsorTokens, updateSuperVotes);
  }

  @Override
  public CompletableFuture<Void> initializeCurrencyAudit() {
    return currencyAudit.initialize();
  }

  @Override
  public CompletableFuture<CurrencyResult> changeCurrency(
      RequestProfile profile, CurrencyChange change, int maxTokens, String serverId) {
    return currencyAudit.change(profile, change, maxTokens, serverId);
  }

  @Override
  public CompletableFuture<Void> queueRefund(PendingRefund refund) {
    return currencyAudit.queueRefund(refund.playerId(), refund.change());
  }

  @Override
  public CompletableFuture<List<PendingRefund>> getPendingRefunds() {
    return currencyAudit.getPendingRefunds();
  }

  @Override
  public CompletableFuture<CurrencyHistoryPage> getCurrencyHistory(
      UUID playerId, Currency currency, int page) {
    return currencyAudit.getHistory(playerId, currency, page);
  }

  @Override
  public CompletableFuture<RequestProfile> query(String target) {
    return service.query(target);
  }

  @Override
  public RequestProfile getCached(UUID playerId) {
    return service.getCached(playerId);
  }
}
