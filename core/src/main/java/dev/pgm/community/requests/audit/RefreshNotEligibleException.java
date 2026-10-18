package dev.pgm.community.requests.audit;

import java.sql.SQLException;

/**
 * Thrown when a token refresh is rejected because the stored profile is no longer eligible
 * (refreshed on another server).
 */
public class RefreshNotEligibleException extends SQLException {

  public RefreshNotEligibleException() {
    super("Token refresh is no longer eligible");
  }

  public static boolean isCause(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof RefreshNotEligibleException) return true;
    }
    return false;
  }
}
