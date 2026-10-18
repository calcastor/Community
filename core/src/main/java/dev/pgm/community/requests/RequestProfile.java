package dev.pgm.community.requests;

import dev.pgm.community.requests.audit.Currency;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import tc.oc.pgm.api.map.MapInfo;

public class RequestProfile {

  private final UUID playerId;
  private Instant lastRequestTime;
  private String lastRequestMap;

  private volatile Instant lastSponsorTime;
  private volatile String lastSponsorMap;

  private volatile int sponsorTokens;
  private volatile Instant lastTokenRefreshTime;

  private volatile int superVotes;
  private volatile Instant lastSuperVote;

  private final AtomicLong version = new AtomicLong();

  public RequestProfile(UUID playerId) {
    this(playerId, null, null, null, null, 0, null, 0, null);
  }

  public RequestProfile(
      UUID playerId,
      Instant lastRequestTime,
      String lastRequestMap,
      Instant lastSponsorTime,
      String lastSponsorMap,
      int sponsorTokens,
      Instant lastTokenRefreshTime,
      int superVotes,
      Instant lastSuperVote) {
    this.playerId = playerId;
    this.lastRequestTime = lastRequestTime;
    this.lastRequestMap = lastRequestMap;
    this.lastSponsorTime = lastSponsorTime;
    this.lastSponsorMap = lastSponsorMap;
    this.sponsorTokens = sponsorTokens;
    this.lastTokenRefreshTime = lastTokenRefreshTime;
    this.superVotes = superVotes;
    this.lastSuperVote = lastSuperVote;
  }

  public long getVersion() {
    return version.get();
  }

  public synchronized boolean copyFromIfUnchanged(RequestProfile other, long expectedVersion) {
    if (version.get() != expectedVersion) return false;
    copyFrom(other);
    return true;
  }

  private synchronized void copyFrom(RequestProfile other) {
    version.incrementAndGet();
    this.lastRequestTime = other.lastRequestTime;
    this.lastRequestMap = other.lastRequestMap;
    this.lastSponsorTime = other.lastSponsorTime;
    this.lastSponsorMap = other.lastSponsorMap;
    this.sponsorTokens = other.sponsorTokens;
    this.lastTokenRefreshTime = other.lastTokenRefreshTime;
    this.superVotes = other.superVotes;
    this.lastSuperVote = other.lastSuperVote;
  }

  public synchronized void applyCurrency(
      Currency currency, int balance, Instant refresh, Instant spent, String map) {
    version.incrementAndGet();
    if (currency == Currency.SPONSOR_TOKEN) {
      this.sponsorTokens = balance;
      this.lastTokenRefreshTime = refresh;
      this.lastSponsorTime = spent;
      this.lastSponsorMap = map;
    } else {
      this.superVotes = balance;
      this.lastSuperVote = spent;
    }
  }

  public int giveSponsorToken(int amount) {
    version.incrementAndGet();
    this.sponsorTokens = Math.max(0, sponsorTokens + amount);
    return sponsorTokens;
  }

  public int giveSuperVotes(int amount) {
    version.incrementAndGet();
    this.superVotes = Math.max(0, superVotes + amount);
    return superVotes;
  }

  public void request(MapInfo map) {
    version.incrementAndGet();
    this.lastRequestMap = map.getId();
    this.lastRequestTime = Instant.now();
  }

  public void sponsor(MapInfo map) {
    version.incrementAndGet();
    this.lastSponsorMap = map.getId();
    this.lastSponsorTime = Instant.now();
    giveSponsorToken(-1);
  }

  public void superVote() {
    version.incrementAndGet();
    this.lastSuperVote = Instant.now();
    giveSuperVotes(-1);
  }

  public Instant getLastRequestTime() {
    return lastRequestTime;
  }

  public String getLastRequestMap() {
    return lastRequestMap;
  }

  public Instant getLastSponsorTime() {
    return lastSponsorTime;
  }

  public String getLastSponsorMap() {
    return lastSponsorMap;
  }

  public int getSponsorTokens() {
    return sponsorTokens;
  }

  public int getSuperVotes() {
    return superVotes;
  }

  public Instant getLastSuperVote() {
    return lastSuperVote;
  }

  public Instant getLastTokenRefreshTime() {
    return lastTokenRefreshTime;
  }

  public void refreshTokens(int amount) {
    version.incrementAndGet();
    giveSponsorToken(amount);
    this.lastTokenRefreshTime = Instant.now();
  }

  public UUID getPlayerId() {
    return playerId;
  }

  public boolean hasWeekElapsed() {
    if (lastTokenRefreshTime == null) return true;
    return Duration.between(lastTokenRefreshTime, Instant.now()).toDays() > 7;
  }

  public boolean hasDayElapsed() {
    if (lastTokenRefreshTime == null) return true;
    return Duration.between(lastTokenRefreshTime, Instant.now()).toHours() > 24;
  }

  @Override
  public String toString() {
    return String.format(
        "RequestProfile{id = %s, tokens = %d, requestMap = %s, sponsorMap = %s, lastRequest = %s, lastSponsor = %s, lastRefresh = %s, superVotes = %s, lastSuperVote = %s}",
        getPlayerId().toString(),
        getSponsorTokens(),
        getLastRequestMap(),
        getLastSponsorMap(),
        getLastRequestTime().toString(),
        getLastSponsorTime().toString(),
        getLastTokenRefreshTime().toString(),
        getSuperVotes(),
        getLastSuperVote().toString());
  }
}
