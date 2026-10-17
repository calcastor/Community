package dev.pgm.community.requests.audit;

import java.sql.SQLException;

public class InsufficientCurrencyException extends SQLException {

  public InsufficientCurrencyException() {
    super("Insufficient currency");
  }

  public static boolean isCause(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof InsufficientCurrencyException) return true;
    }
    return false;
  }
}
