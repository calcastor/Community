package dev.pgm.community.requests.audit;

public enum CurrencyAction {
  ADMIN_GRANT,
  ADMIN_REMOVE,
  DAILY_REFRESH,
  WEEKLY_REFRESH,
  SPEND,
  REFUND;

  public boolean isTokenRefresh() {
    return this == DAILY_REFRESH || this == WEEKLY_REFRESH;
  }
}
