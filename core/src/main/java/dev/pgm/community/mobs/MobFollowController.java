package dev.pgm.community.mobs;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;

final class MobFollowController {

  private static final double STOP_DISTANCE_SQUARED = 2 * 2;
  private static final double RESUME_DISTANCE_SQUARED = 3 * 3;
  private static final double TARGET_MOVEMENT_SQUARED = 1;
  private static final long RETRY_UPDATES = 4;

  private final Map<UUID, Request> requests = new HashMap<>();

  enum Action {
    NONE,
    FOLLOW,
    STOP
  }

  Action update(
      UUID mobId,
      UUID targetId,
      Location mobLocation,
      Location targetLocation,
      float speed,
      long update,
      boolean attacking) {
    if (attacking) {
      requests.remove(mobId);
      return Action.FOLLOW;
    }

    Request previous = requests.get(mobId);
    boolean sameTarget = previous != null
        && previous.targetId().equals(targetId)
        && previous.destination().getWorld().equals(targetLocation.getWorld());
    double distance = mobLocation.distanceSquared(targetLocation);
    if (sameTarget && previous.stopped() && distance <= RESUME_DISTANCE_SQUARED) {
      return Action.NONE;
    }
    if (distance <= STOP_DISTANCE_SQUARED) {
      requests.put(mobId, new Request(targetId, targetLocation.clone(), speed, update, true));
      return Action.STOP;
    }

    if (!sameTarget
        || previous.stopped()
        || Float.compare(previous.speed(), speed) != 0
        || previous.destination().distanceSquared(targetLocation) >= TARGET_MOVEMENT_SQUARED
        || update - previous.update() >= RETRY_UPDATES) {
      requests.put(mobId, new Request(targetId, targetLocation.clone(), speed, update, false));
      return Action.FOLLOW;
    }
    return Action.NONE;
  }

  void remove(UUID mobId) {
    requests.remove(mobId);
  }

  void clear() {
    requests.clear();
  }

  private record Request(
      UUID targetId, Location destination, float speed, long update, boolean stopped) {}
}
