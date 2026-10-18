package dev.pgm.community.network.updates.types;

import dev.pgm.community.network.Channels;
import dev.pgm.community.network.updates.NetworkUpdate;
import java.util.UUID;

/** RequestUpdate - Tells other servers a player's request profile has changed */
public class RequestUpdate implements NetworkUpdate {

  private final UUID playerId;

  public RequestUpdate(UUID playerId) {
    this.playerId = playerId;
  }

  @Override
  public String getChannel() {
    return Channels.REQUEST_UPDATE;
  }

  @Override
  public String getData() {
    return playerId.toString();
  }
}
