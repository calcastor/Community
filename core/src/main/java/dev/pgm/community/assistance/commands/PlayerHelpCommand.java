package dev.pgm.community.assistance.commands;

import dev.pgm.community.Community;
import dev.pgm.community.CommunityCommand;
import dev.pgm.community.assistance.feature.AssistanceFeature;
import dev.pgm.community.moderation.feature.ModerationFeature;
import dev.pgm.community.moderation.punishments.types.MutePunishment;
import dev.pgm.community.utils.CommandAudience;
import java.util.Optional;
import org.bukkit.entity.Player;
import tc.oc.pgm.lib.org.incendo.cloud.annotation.specifier.Greedy;
import tc.oc.pgm.lib.org.incendo.cloud.annotations.Argument;
import tc.oc.pgm.lib.org.incendo.cloud.annotations.Command;
import tc.oc.pgm.lib.org.incendo.cloud.annotations.CommandDescription;

public class PlayerHelpCommand extends CommunityCommand {

  private AssistanceFeature assistance;
  private ModerationFeature moderation;

  public PlayerHelpCommand() {
    this.assistance = Community.get().getFeatures().getReports();
    this.moderation = Community.get().getFeatures().getModeration();
  }

  @Command("assistance|assist|helpop|helpme <reason>")
  @CommandDescription("Request help from a staff member")
  public void assistanceCommand(
      CommandAudience viewer, Player player, @Argument("reason") @Greedy String reason) {
    Optional<MutePunishment> mute = moderation.getCachedMute(player.getUniqueId());
    if (mute.isPresent()) {
      viewer.sendWarning(mute.get().getChatMuteMessage());
      return;
    }

    if (!assistance.canRequest(player.getUniqueId())) {
      int cooldown = assistance.getCooldownSeconds(player.getUniqueId());
      if (cooldown > 0) {
        viewer.sendWarning(assistance.getCooldownMessage(player.getUniqueId()));
        return;
      }
    }
    assistance.assist(player, reason);
  }
}
