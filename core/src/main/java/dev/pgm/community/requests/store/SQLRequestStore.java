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
import dev.pgm.community.requests.services.SQLSponsorClaimService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SQLRequestStore implements RequestStore {

  private final SQLRequestService service;
  private final SQLCurrencyAuditService currencyAudit;
  private final SQLSponsorClaimService sponsorClaims;

  public SQLRequestStore() {
    this.service = new SQLRequestService();
    this.currencyAudit = new SQLCurrencyAuditService(DatabaseExecutor.getDialect());
    this.sponsorClaims = new SQLSponsorClaimService(DatabaseExecutor.getDialect());
  }

  @Override
  public CompletableFuture<RequestProfile> login(UUID playerId, boolean awaitPersistence) {
    return service.login(playerId, awaitPersistence);
  }

  @Override
  public CompletableFuture<Void> update(
      RequestProfile profile, boolean updateSponsorTokens, boolean updateSuperVotes) {
    return service.update(profile, updateSponsorTokens, updateSuperVotes);
  }

  @Override
  public void invalidate(UUID playerId) {
    service.invalidate(playerId);
  }

  @Override
  public CompletableFuture<RequestProfile> refresh(UUID playerId) {
    return service.refresh(playerId);
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
  public CompletableFuture<Optional<String>> claimSponsor(
      UUID playerId, String serverId, String mapId) {
    return sponsorClaims.claim(playerId, serverId, mapId);
  }

  @Override
  public CompletableFuture<Boolean> ownsSponsorClaim(UUID playerId, String serverId) {
    return sponsorClaims.owns(playerId, serverId);
  }

  @Override
  public CompletableFuture<Void> releaseSponsorClaim(UUID playerId, String serverId) {
    return sponsorClaims.release(playerId, serverId);
  }

  @Override
  public CompletableFuture<Void> releaseServerSponsorClaims(String serverId) {
    return sponsorClaims.releaseAll(serverId);
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
