package dev.pgm.community.requests.audit;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record CurrencyHistoryEntry(
    UUID eventId,
    Instant time,
    CurrencyAction action,
    int requestedAmount,
    int actualChange,
    int balanceBefore,
    int balanceAfter,
    String actorName,
    @Nullable String context,
    String serverId) {
  public static final int PAGE_SIZE = 10;
}
