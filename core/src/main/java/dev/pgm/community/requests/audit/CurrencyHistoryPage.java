package dev.pgm.community.requests.audit;

import java.util.List;

public record CurrencyHistoryPage(List<CurrencyHistoryEntry> entries, int total) {

  public int pages() {
    return Math.max(
        1, (total + CurrencyHistoryEntry.PAGE_SIZE - 1) / CurrencyHistoryEntry.PAGE_SIZE);
  }
}
