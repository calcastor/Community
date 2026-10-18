package dev.pgm.community.network.subs.types;

import dev.pgm.community.network.Channels;
import dev.pgm.community.network.subs.NetworkSubscriber;
import dev.pgm.community.requests.feature.RequestFeature;
import java.util.UUID;
import java.util.logging.Logger;

/** SponsorClaimSubscriber - Removes sponsor requests replaced by a newer one on another server */
public class SponsorClaimSubscriber extends NetworkSubscriber {

  private final RequestFeature requests;

  public SponsorClaimSubscriber(RequestFeature requests, String networkId, Logger logger) {
    super(Channels.SPONSOR_CLAIM, networkId, logger);
    this.requests = requests;
  }

  @Override
  public void onReceiveUpdate(String data) {
    try {
      requests.receiveSponsorClaim(UUID.fromString(data));
    } catch (IllegalArgumentException e) {
      logger.warning(String.format(
          "Invalid UUID (%s) received for message channel (%s)", data, Channels.SPONSOR_CLAIM));
    }
  }
}
