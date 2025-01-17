package dev.pgm.community.chat.network;

import static dev.pgm.community.utils.NetworkUtils.getServer;
import static net.kyori.adventure.text.Component.text;

import dev.pgm.community.CommunityPermissions;
import dev.pgm.community.feature.FeatureBase;
import dev.pgm.community.network.feature.NetworkFeature;
import dev.pgm.community.network.subs.types.ChatSubscriber;
import dev.pgm.community.network.updates.types.ChatUpdate;
import dev.pgm.community.utils.BroadcastUtils;
import dev.pgm.community.utils.PGMUtils;
import dev.pgm.community.utils.Sounds;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.Configuration;
import org.bukkit.event.EventHandler;
import tc.oc.pgm.api.event.ChannelMessageEvent;
import tc.oc.pgm.channels.AdminChannel;

public class NetworkChatFeature extends FeatureBase {

  public static final String ADMIN_CHANNEL = "admin";

  private NetworkFeature network;

  public NetworkChatFeature(Configuration config, Logger logger, NetworkFeature network) {
    super(new NetworkChatConfig(config), logger, "Network Chat (PGM)");
    this.network = network;

    if (getConfig().isEnabled() && PGMUtils.isPGMEnabled() && network.isEnabled()) {
      enable();
      network.registerSubscriber(new ChatSubscriber(this, network.getNetworkId(), logger));
    }
  }

  @EventHandler
  public void onMatchPlayerChat(ChannelMessageEvent<?> event) {
    if (event.getChannel() instanceof AdminChannel) {
      network.sendUpdate(new ChatUpdate(new NetworkChatMessage(event, getServer())));
    } // TODO: maybe more cross server message types?
  }

  public void recieveUpdate(NetworkChatMessage message) {
    if (message.getChannel().equalsIgnoreCase(ADMIN_CHANNEL)) {
      Component formatted = formatMessage(message.getSender(), message.getMessage());
      BroadcastUtils.sendAdminChatMessage(
          formatted,
          message.getServer(),
          Sounds.ADMIN_CHAT,
          CommunityPermissions.CROSS_NETWORK_CHAT);
    }
  }

  private Component formatMessage(Component sender, Component message) {
    return text()
        .append(sender)
        .append(text(": ", NamedTextColor.WHITE))
        .append(message)
        .build();
  }
}
