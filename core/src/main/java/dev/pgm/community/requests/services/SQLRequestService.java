package dev.pgm.community.requests.services;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import dev.pgm.community.database.DatabaseExecutor;
import dev.pgm.community.feature.SQLFeatureBase;
import dev.pgm.community.requests.RequestProfile;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

public class SQLRequestService extends SQLFeatureBase<RequestProfile, String>
    implements RequestQuery {

  private static final int REFRESH_ATTEMPTS = 3;

  private final LoadingCache<UUID, UserRequestData> profileCache;

  public SQLRequestService() {
    super(TABLE_NAME, TABLE_FIELDS);

    this.profileCache = CacheBuilder.newBuilder().build(CacheLoader.from(UserRequestData::new));
  }

  public CompletableFuture<RequestProfile> login(UUID playerId, boolean awaitPersistence) {
    if (!awaitPersistence) {
      return refresh(playerId).thenApplyAsync(profile -> {
        if (profile != null) return profile;
        RequestProfile created = new RequestProfile(playerId);
        save(created);
        return created;
      });
    }
    return getTableReady().thenCompose(ignored -> refresh(playerId)).thenCompose(profile -> {
      if (profile != null) return CompletableFuture.completedFuture(profile);
      return DatabaseExecutor.executeUpdateAsync(
              INSERT_REQUESTS_QUERY, playerId.toString(), -1L, null, -1L, null, 0, -1L, 0, -1L)
          .thenCompose(count -> {
            profileCache.invalidate(playerId);
            return query(playerId.toString());
          });
    });
  }

  @Nullable
  public RequestProfile getCached(UUID playerId) {
    UserRequestData data = profileCache.getUnchecked(playerId);
    if (data.getProfile() != null) {
      return data.getProfile();
    }
    return null;
  }

  @Override
  public void save(RequestProfile profile) {
    DatabaseExecutor.executeUpdateAsync(
        INSERT_REQUESTS_QUERY,
        profile.getPlayerId().toString(),
        convertTime(profile.getLastRequestTime()),
        profile.getLastRequestMap(),
        convertTime(profile.getLastSponsorTime()),
        profile.getLastSponsorMap(),
        profile.getSponsorTokens(),
        convertTime(profile.getLastTokenRefreshTime()),
        profile.getSuperVotes(),
        convertTime(profile.getLastSuperVote()));
    profileCache.invalidate(profile.getPlayerId());
  }

  public CompletableFuture<Void> update(
      RequestProfile profile, boolean updateSponsorTokens, boolean updateSuperVotes) {
    // Audited currency fields are owned exclusively by the transactional currency service.
    String sql = "UPDATE " + TABLE_NAME + " SET " + REQUEST_UPDATE_FIELDS;
    List<Object> params = new ArrayList<>();
    params.add(convertTime(profile.getLastRequestTime()));
    params.add(profile.getLastRequestMap());
    if (updateSponsorTokens) {
      sql += ", " + SPONSOR_UPDATE_FIELDS;
      params.add(convertTime(profile.getLastSponsorTime()));
      params.add(profile.getLastSponsorMap());
      params.add(profile.getSponsorTokens());
      params.add(convertTime(profile.getLastTokenRefreshTime()));
    }
    if (updateSuperVotes) {
      sql += ", " + SUPER_VOTE_UPDATE_FIELDS;
      params.add(profile.getSuperVotes());
      params.add(convertTime(profile.getLastSuperVote()));
    }
    params.add(profile.getPlayerId().toString());
    return DatabaseExecutor.executeUpdateAsync(sql + " WHERE id = ?", params.toArray())
        .thenApply(ignored -> null);
  }

  public void invalidate(UUID playerId) {
    profileCache.invalidate(playerId);
  }

  @Override
  public CompletableFuture<List<RequestProfile>> queryList(String target) {
    return CompletableFuture.completedFuture(Lists.newArrayList());
  }

  @Override
  public CompletableFuture<RequestProfile> query(String target) {
    UUID playerId = UUID.fromString(target);
    UserRequestData profile = profileCache.getUnchecked(playerId);

    if (profile.isLoaded() && profile.getProfile() != null) {
      return CompletableFuture.completedFuture(profile.getProfile());
    }
    return load(playerId).thenApplyAsync(result -> {
      if (result != null) {
        profile.setProfile(result);
      }
      profile.setLoaded(true);
      return profile.getProfile();
    });
  }

  public CompletableFuture<RequestProfile> refresh(UUID playerId) {
    UserRequestData data = profileCache.getIfPresent(playerId);
    if (data == null || data.getProfile() == null) return query(playerId.toString());
    return reload(playerId, data.getProfile(), REFRESH_ATTEMPTS);
  }

  private CompletableFuture<RequestProfile> reload(
      UUID playerId, RequestProfile cached, int attempts) {
    long version = cached.getVersion();
    return load(playerId).thenCompose(fresh -> {
      if (fresh == null || cached.copyFromIfUnchanged(fresh, version)) {
        return CompletableFuture.completedFuture(cached);
      }

      if (attempts > 1) return reload(playerId, cached, attempts - 1);
      return CompletableFuture.completedFuture(cached);
    });
  }

  private CompletableFuture<RequestProfile> load(UUID playerId) {
    return DatabaseExecutor.queryFirstAsync(
        SELECT_REQUEST_QUERY,
        result -> {
          final UUID id = UUID.fromString(result.getString("id"));
          final long lastRequest = result.getLong("last_request_time");
          final String lastRequestMap = result.getString("last_request_map");
          final long lastSponsor = result.getLong("last_sponsor_time");
          final String lastSponsorMap = result.getString("last_sponsor_map");
          final int tokens = result.getInt("tokens");
          final long lastToken = result.getLong("last_token_refresh");
          final int superVotes = result.getInt("super_votes");
          final long lastSuperVote = result.getLong("last_super_vote");

          final Instant lastRequestTime =
              lastRequest == -1 ? null : Instant.ofEpochMilli(lastRequest);
          final Instant lastSponsorTime =
              lastSponsor == -1 ? null : Instant.ofEpochMilli(lastSponsor);
          final Instant lastTokenRefreshTime =
              lastToken == -1 ? null : Instant.ofEpochMilli(lastToken);
          final Instant lastSuperVoteTime =
              lastSuperVote == -1 ? null : Instant.ofEpochMilli(lastSuperVote);

          return new RequestProfile(
              id,
              lastRequestTime,
              lastRequestMap,
              lastSponsorTime,
              lastSponsorMap,
              tokens,
              lastTokenRefreshTime,
              superVotes,
              lastSuperVoteTime);
        },
        playerId.toString());
  }

  private long convertTime(Instant time) {
    if (time == null) {
      return -1;
    }
    return time.toEpochMilli();
  }

  private static class UserRequestData {

    private final UUID playerId;
    private RequestProfile profile;
    private boolean loaded;

    public UserRequestData(UUID playerId) {
      this.playerId = playerId;
      this.profile = null;
    }

    public UUID getPlayerId() {
      return playerId;
    }

    public RequestProfile getProfile() {
      return profile;
    }

    public void setProfile(RequestProfile profile) {
      this.profile = profile;
    }

    public boolean isLoaded() {
      return loaded;
    }

    public void setLoaded(boolean loaded) {
      this.loaded = loaded;
    }
  }
}
