package dev.pgm.community.requests.audit;

import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static tc.oc.pgm.util.text.TemporalComponent.relativePastApproximate;

import dev.pgm.community.Community;
import dev.pgm.community.utils.BroadcastUtils;
import dev.pgm.community.utils.CommandAudience;
import dev.pgm.community.utils.MessageUtils;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.map.MapInfo;
import tc.oc.pgm.util.named.MapNameStyle;
import tc.oc.pgm.util.text.TextFormatter;

public final class CurrencyHistoryComponents {

  private CurrencyHistoryComponents() {}

  public static void show(
      CommandAudience audience,
      CurrencyHistoryPage history,
      UUID playerId,
      Component name,
      Currency currency,
      int page,
      boolean auditingEnabled) {
    boolean tokens = currency == Currency.SPONSOR_TOKEN;

    if (history.total() == 0) {
      audience.sendWarning(text(tokens ? "No sponsor token" : "No super vote")
          .append(text(" history found for "))
          .append(name));
      sendAuditingDisabled(audience, auditingEnabled);
      return;
    }

    int pages = history.pages();
    if (page > pages) {
      audience.sendWarning(translatable("command.invalidPage", text(page), text(pages)));
      return;
    }

    Component title = text()
        .append(text(tokens ? "Token History" : "Super Vote History"))
        .append(text(" - ", NamedTextColor.GRAY))
        .append(name)
        .hoverEvent(HoverEvent.showText(text(
            "Note: only changes recorded while auditing was enabled are shown.",
            NamedTextColor.GRAY)))
        .build();

    Component paginated = TextFormatter.paginate(
        title, page, pages, NamedTextColor.DARK_AQUA, NamedTextColor.AQUA, true);

    audience.sendMessage(TextFormatter.horizontalLineHeading(
        audience.getSender(), paginated, NamedTextColor.DARK_PURPLE, 250));

    for (CurrencyHistoryEntry entry : history.entries()) {
      audience.sendMessage(formatEntry(entry, tokens));
    }

    if (pages > 1) {
      String command = (tokens ? "/tokens" : "/supervotes") + " history " + playerId;
      audience.sendMessage(TextFormatter.horizontalLineHeading(
          audience.getSender(),
          formatPageButtons(command, page, pages),
          NamedTextColor.DARK_PURPLE,
          250));
    }

    sendAuditingDisabled(audience, auditingEnabled);
  }

  private static void sendAuditingDisabled(CommandAudience audience, boolean auditingEnabled) {
    if (!auditingEnabled) {
      audience.sendWarning(text("Auditing is disabled, new changes are not being recorded"));
    }
  }

  private static Component formatEntry(CurrencyHistoryEntry entry, boolean tokens) {
    int amount = entry.actualChange() != 0 ? entry.actualChange() : entry.requestedAmount();
    boolean add = amount > 0;

    TextComponent.Builder hover = text()
        .append(text("Balance ", NamedTextColor.GRAY))
        .append(BroadcastUtils.RIGHT_DIV.color(NamedTextColor.GOLD))
        .append(space())
        .append(text(entry.balanceBefore(), NamedTextColor.YELLOW))
        .append(text(" to ", NamedTextColor.GRAY))
        .append(text(entry.balanceAfter(), NamedTextColor.YELLOW));

    // Balances never go below zero, so a removal may have taken less than requested
    if (entry.actualChange() != entry.requestedAmount()) {
      hover
          .append(newline())
          .append(text("Requested ", NamedTextColor.GRAY))
          .append(BroadcastUtils.RIGHT_DIV.color(NamedTextColor.GOLD))
          .append(space())
          .append(text(entry.requestedAmount(), NamedTextColor.YELLOW));
    }

    // Like reports and punishments, only note the server when it was a different one
    if (!entry.serverId().equalsIgnoreCase(Community.get().getServerConfig().getServerId())) {
      hover
          .append(newline())
          .append(text("Server ", NamedTextColor.GRAY))
          .append(BroadcastUtils.RIGHT_DIV.color(NamedTextColor.GOLD))
          .append(space())
          .append(text(entry.serverId(), NamedTextColor.AQUA));
    }

    return text()
        .append(text(
            add ? "+" : "-", add ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD))
        .append(text(
            Math.abs(amount) + " ",
            tokens ? NamedTextColor.YELLOW : NamedTextColor.DARK_PURPLE,
            TextDecoration.BOLD))
        .append(tokens ? MessageUtils.TOKEN : MessageUtils.VOTE)
        .append(space())
        .append(formatAction(entry, tokens))
        .append(BroadcastUtils.BROADCAST_DIV.color(NamedTextColor.GOLD))
        .append(relativePastApproximate(entry.time()).color(NamedTextColor.YELLOW))
        .color(NamedTextColor.GRAY)
        .hoverEvent(HoverEvent.showText(hover.build()))
        .build();
  }

  private static Component formatAction(CurrencyHistoryEntry entry, boolean tokens) {
    Component actor = text(entry.actorName(), NamedTextColor.DARK_AQUA);
    return switch (entry.action()) {
      case ADMIN_GRANT -> text("Given by ").append(actor);
      case ADMIN_REMOVE -> text("Removed by ").append(actor);
      case DAILY_REFRESH -> text("Daily token refresh");
      case WEEKLY_REFRESH -> text("Weekly token refresh");
      case SPEND ->
        tokens
            ? text("Sponsored ").append(formatMap(entry.context()))
            : text("Activated a super vote");
      case REFUND -> text(tokens ? "Sponsor token refunded" : "Super vote refunded");
    };
  }

  private static Component formatMap(String mapId) {
    MapInfo map = mapId == null ? null : PGM.get().getMapLibrary().getMap(mapId);
    if (map == null) return text(mapId == null ? "a map" : mapId, NamedTextColor.GOLD);
    return map.getStyledName(MapNameStyle.COLOR);
  }

  private static Component formatPageButtons(String command, int page, int pages) {
    TextComponent.Builder buttons = text();

    if (page > 1) {
      buttons.append(text()
          .append(BroadcastUtils.LEFT_DIV.color(NamedTextColor.GOLD))
          .append(text(" Previous Page", NamedTextColor.BLUE))
          .hoverEvent(HoverEvent.showText(text("Click to view previous page", NamedTextColor.GRAY)))
          .clickEvent(ClickEvent.runCommand(command + " " + (page - 1))));
    }

    if (page > 1 && page < pages) {
      buttons.append(text(" | ", NamedTextColor.DARK_GRAY));
    }

    if (page < pages) {
      buttons.append(text()
          .append(text("Next Page ", NamedTextColor.BLUE))
          .append(BroadcastUtils.RIGHT_DIV.color(NamedTextColor.GOLD))
          .hoverEvent(HoverEvent.showText(text("Click to view next page", NamedTextColor.GRAY)))
          .clickEvent(ClickEvent.runCommand(command + " " + (page + 1))));
    }

    return buttons.build();
  }
}
