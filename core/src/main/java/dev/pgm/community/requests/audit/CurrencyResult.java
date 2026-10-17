package dev.pgm.community.requests.audit;

/**
 * The outcome of a committed currency change.
 *
 * @param balanceBefore balance before the change was applied
 * @param balanceAfter balance after the change was applied
 * @param duplicate whether the change had already been recorded, so nothing new was applied
 */
public record CurrencyResult(int balanceBefore, int balanceAfter, boolean duplicate) {}
