package dev.pgm.community.requests.commands.supervotes;

import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;

import dev.pgm.community.Community;
import dev.pgm.community.CommunityCommand;
import dev.pgm.community.CommunityPermissions;
import dev.pgm.community.commands.player.TargetPlayer;
import dev.pgm.community.requests.RequestConfig;
import dev.pgm.community.requests.RequestProfile;
import dev.pgm.community.requests.audit.Currency;
import dev.pgm.community.requests.audit.CurrencyChange;
import dev.pgm.community.requests.audit.CurrencyHistoryComponents;
import dev.pgm.community.requests.feature.RequestFeature;
import dev.pgm.community.requests.supervotes.SuperVoteComponents;
import dev.pgm.community.users.feature.UsersFeature;
import dev.pgm.community.utils.CommandAudience;
import dev.pgm.community.utils.MessageUtils;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import tc.oc.pgm.lib.org.incendo.cloud.annotation.specifier.Range;
import tc.oc.pgm.lib.org.incendo.cloud.annotations.Argument;
import tc.oc.pgm.lib.org.incendo.cloud.annotations.Command;
import tc.oc.pgm.lib.org.incendo.cloud.annotations.CommandDescription;
import tc.oc.pgm.lib.org.incendo.cloud.annotations.Default;
import tc.oc.pgm.lib.org.incendo.cloud.annotations.Permission;
import tc.oc.pgm.util.named.NameStyle;

@Command("supervotes")
public class SuperVoteAdminCommands extends CommunityCommand {

  private final UsersFeature users;
  private final RequestFeature requests;

  public SuperVoteAdminCommands() {
    this.users = Community.get().getFeatures().getUsers();
    this.requests = Community.get().getFeatures().getRequests();
  }

  @Command("[target]")
  @CommandDescription("Check your super vote balance")
  public void balance(CommandAudience audience, @Argument(value = "target") TargetPlayer target) {
    if (target != null && audience.hasPermission(CommunityPermissions.SUPER_VOTE_BALANCE)) {
      getTarget(target.getIdentifier(), users).thenAcceptAsync(uuid -> {
        if (uuid.isPresent()) {
          RequestProfile profile = requests.getRequestProfile(uuid.get()).join();
          if (profile == null) {
            audience.sendWarning(formatNotFoundComponent(target.getIdentifier()));
            return;
          }

          Component name = users.renderUsername(uuid, NameStyle.FANCY).join();
          audience.sendMessage(
              SuperVoteComponents.getSuperVoteBalance(name, profile.getSuperVotes()));
        } else {
          audience.sendWarning(formatNotFoundComponent(target.getIdentifier()));
        }
      });
    } else if (audience.isPlayer()) {
      Player player = audience.getPlayer();
      requests.getRequestProfile(player.getUniqueId()).thenAcceptAsync(profile -> {
        int superVotes = profile.getSuperVotes();
        audience.sendMessage(SuperVoteComponents.getSuperVoteBalance(superVotes));
      });
    } else {
      audience.sendWarning(text("Please provide a username to check the super vote balance of."));
    }
  }

  @Command("history <target> [page]")
  @CommandDescription("View recorded super vote grants and spending")
  @Permission(CommunityPermissions.SUPER_VOTE_HISTORY)
  public void history(
      CommandAudience audience,
      @Argument("target") TargetPlayer target,
      @Argument("page") @Default("1") @Range(min = "1") int page) {
    getTarget(target.getIdentifier(), users)
        .thenAccept(id -> {
          if (id.isEmpty()) {
            audience.sendWarning(formatNotFoundComponent(target.getIdentifier()));
            return;
          }
          requests
              .getCurrencyHistory(id.get(), Currency.SUPER_VOTE, page)
              .thenAcceptAsync(history -> {
                Component name = users
                    .renderUsername(id, NameStyle.FANCY)
                    .exceptionally(error -> text(target.getIdentifier()))
                    .join();
                CurrencyHistoryComponents.show(
                    audience,
                    history,
                    id.get(),
                    name,
                    Currency.SUPER_VOTE,
                    page,
                    ((RequestConfig) requests.getConfig()).isSuperVoteAuditEnabled());
              })
              .exceptionally(failure -> {
                Community.get()
                    .getLogger()
                    .log(
                        Level.WARNING,
                        "Failed to load super vote history for " + target.getIdentifier(),
                        failure);
                audience.sendWarning(text("Failed to load super vote history", NamedTextColor.RED));
                return null;
              });
        })
        .exceptionally(failure -> {
          audience.sendWarning(text("Could not resolve that player. Please try again!"));
          return null;
        });
  }

  @Command("give <target> <amount>")
  @CommandDescription("Give the target player super votes")
  @Permission(CommunityPermissions.ADMIN)
  public void give(
      CommandAudience audience,
      @Argument("target") TargetPlayer target,
      @Argument("amount") int amount) {
    CurrencyChange change = CurrencyChange.admin(
        Currency.SUPER_VOTE,
        amount,
        audience.getId().orElse(null),
        audience.getSender().getName());
    getTarget(target.getIdentifier(), users).thenAcceptAsync(targetId -> {
      if (targetId.isPresent()) {
        RequestProfile profile = requests.getRequestProfile(targetId.get()).join();
        if (profile != null) {
          requests
              .changeCurrency(profile.getPlayerId(), change)
              .whenCompleteAsync((total, failure) -> {
                if (failure != null) {
                  audience.sendWarning(text("Could not save super votes. Please try again!"));
                  return;
                }
                audience.sendMessage(text()
                    .append(MessageUtils.VOTE)
                    .append(space())
                    .append(users
                        .renderUsername(profile.getPlayerId(), NameStyle.FANCY)
                        .exceptionally(error -> text(target.getIdentifier()))
                        .join())
                    .append(text(" now has "))
                    .append(text(total, NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                    .append(text(" super vote" + (total != 1 ? "s" : "")))
                    .color(NamedTextColor.GRAY)
                    .build());
              });
          return;
        }
      }
      audience.sendWarning(formatNotFoundComponent(target.getIdentifier()));
    });
  }
}
