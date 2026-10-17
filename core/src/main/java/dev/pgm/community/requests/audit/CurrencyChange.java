package dev.pgm.community.requests.audit;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record CurrencyChange(
    UUID eventId,
    Currency currency,
    int amount,
    CurrencyAction action,
    @Nullable UUID actorId,
    String actorName,
    @Nullable String context,
    @Nullable UUID relatedEventId) {

  public static CurrencyChange admin(
      Currency currency, int amount, @Nullable UUID actorId, String actorName) {
    return new CurrencyChange(
        UUID.randomUUID(),
        currency,
        amount,
        amount < 0 ? CurrencyAction.ADMIN_REMOVE : CurrencyAction.ADMIN_GRANT,
        actorId,
        actorName,
        null,
        null);
  }

  public static CurrencyChange system(
      Currency currency, int amount, CurrencyAction action, @Nullable String context) {
    return new CurrencyChange(
        UUID.randomUUID(), currency, amount, action, null, "SYSTEM", context, null);
  }
}
