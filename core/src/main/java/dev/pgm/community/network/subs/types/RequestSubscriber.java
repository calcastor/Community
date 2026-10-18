package dev.pgm.community.network.subs.types;

import dev.pgm.community.network.Channels;
import dev.pgm.community.network.subs.NetworkSubscriber;
import dev.pgm.community.requests.feature.RequestFeature;
import java.util.UUID;
import java.util.logging.Logger;

/** RequestSubscriber - Invalidates cached request profiles when they change on another server */
public class RequestSubscriber extends NetworkSubscriber {

  private final RequestFeature requests;

  public RequestSubscriber(RequestFeature requests, String networkId, Logger logger) {
    super(Channels.REQUEST_UPDATE, networkId, logger);
    this.requests = requests;
  }

  @Override
  public void onReceiveUpdate(String data) {
    try {
      requests.receiveNetworkInvalidation(UUID.fromString(data));
    } catch (IllegalArgumentException e) {
      logger.warning(String.format(
          "Invalid UUID (%s) received for message channel (%s)", data, Channels.REQUEST_UPDATE));
    }
  }
}
