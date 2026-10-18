package dev.pgm.community.network.updates.types;

import dev.pgm.community.network.Channels;
import dev.pgm.community.network.updates.NetworkUpdate;
import java.util.UUID;

/** SponsorClaimUpdate - Tells other servers a player's sponsor slot was claimed by this server */
public class SponsorClaimUpdate implements NetworkUpdate {

  private final UUID playerId;

  public SponsorClaimUpdate(UUID playerId) {
    this.playerId = playerId;
  }

  @Override
  public String getChannel() {
    return Channels.SPONSOR_CLAIM;
  }

  @Override
  public String getData() {
    return playerId.toString();
  }
}
