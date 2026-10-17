package dev.pgm.community.requests.audit;

import java.util.UUID;

public record PendingRefund(UUID playerId, CurrencyChange change) {}
