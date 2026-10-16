package dev.pgm.community.mobs;

import static dev.pgm.community.util.EntityUtils.ENTITY_UTILS;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import dev.pgm.community.Community;
import dev.pgm.community.feature.FeatureBase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitTask;

public class MobFeature extends FeatureBase {

  public static final float DEFAULT_SPEED = 1.2f;
  private static final String OWNER_METADATA = "owner";

  private final Map<UUID, Map<UUID, LivingEntity>> ownedMobs;
  private final Map<UUID, UUID> followTargets;
  private final Set<UUID> attackers;
  private final MobFollowController followController = new MobFollowController();

  private BukkitTask task;
  private float speed;
  private long followUpdate;

  public MobFeature(Configuration config, Logger logger) {
    super(new MobConfig(config), logger, "Mobs");
    this.ownedMobs = Maps.newHashMap();
    this.followTargets = Maps.newHashMap();
    this.attackers = Sets.newHashSet();
    this.speed = DEFAULT_SPEED;
    if (getMobConfig().isEnabled()) {
      enable();
    }
  }

  @Override
  public void enable() {
    if (this.task != null) return;
    super.enable();
    Bukkit.getWorlds().forEach(world -> world.getLivingEntities().forEach(this::trackMob));
    this.task = Community.get()
        .getServer()
        .getScheduler()
        .runTaskTimer(Community.get(), this::updateFollows, 0L, 10L);
  }

  @Override
  public void disable() {
    super.disable();
    if (this.task != null) {
      this.task.cancel();
      this.task = null;
    }
    ownedMobs.clear();
    followController.clear();
  }

  private void trackMob(LivingEntity mob) {
    for (MetadataValue value : mob.getMetadata(OWNER_METADATA)) {
      if (value.getOwningPlugin() == Community.get() && value.value() instanceof UUID ownerId) {
        ownedMobs
            .computeIfAbsent(ownerId, ignored -> Maps.newHashMap())
            .put(mob.getUniqueId(), mob);
        return;
      }
    }
  }

  private void untrackMob(Entity entity) {
    followController.remove(entity.getUniqueId());
    for (MetadataValue value : entity.getMetadata(OWNER_METADATA)) {
      if (value.getOwningPlugin() == Community.get() && value.value() instanceof UUID ownerId) {
        Map<UUID, LivingEntity> mobs = ownedMobs.get(ownerId);
        if (mobs != null) {
          mobs.remove(entity.getUniqueId());
          if (mobs.isEmpty()) ownedMobs.remove(ownerId);
        }
        return;
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onDeath(EntityDeathEvent event) {
    untrackMob(event.getEntity());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onChunkLoad(ChunkLoadEvent event) {
    for (Entity entity : event.getChunk().getEntities()) {
      if (entity instanceof LivingEntity mob) trackMob(mob);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onChunkUnload(ChunkUnloadEvent event) {
    for (Entity entity : event.getChunk().getEntities()) {
      untrackMob(entity);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onWorldUnload(WorldUnloadEvent event) {
    ownedMobs
        .values()
        .forEach(mobs -> mobs.values().removeIf(mob -> {
          if (!mob.getWorld().equals(event.getWorld())) return false;
          followController.remove(mob.getUniqueId());
          return true;
        }));
    ownedMobs.values().removeIf(Map::isEmpty);
  }

  @EventHandler
  public void onDamage(EntityDamageByEntityEvent event) {
    if (event.getDamager() != null
        && event.getDamager() instanceof Player damager
        && event.getEntity() instanceof Player target) {

      if (isAttacker(damager.getUniqueId())) {
        setTarget(damager, target);
      }
    }
  }

  public void updateFollows() {
    followUpdate++;
    this.followTargets.forEach((key, value) -> {
      Player owner = Bukkit.getPlayer(key);
      Player target = Bukkit.getPlayer(value);
      if (owner != null && target != null && owner.getWorld().equals(target.getWorld())) {
        Location targetLocation = target.getLocation();
        boolean attacking = attackers.contains(key);
        this.getOwnedMobs(owner).forEach(mob -> {
          MobFollowController.Action action = followController.update(
              mob.getUniqueId(),
              value,
              mob.getLocation(),
              targetLocation,
              speed,
              followUpdate,
              attacking);
          switch (action) {
            case FOLLOW -> ENTITY_UTILS.follow(mob, targetLocation, speed);
            case STOP -> ENTITY_UTILS.stopFollowing(mob);
            case NONE -> {}
          }
          if (mob instanceof Creature creature && attacking) {
            creature.setTarget(target);
          }
        });
      }
    });
  }

  public boolean isFollower(UUID playerId) {
    return followTargets.containsKey(playerId);
  }

  public boolean isAttacker(UUID playerId) {
    return attackers.contains(playerId);
  }

  public void setSpeed(float speed) {
    this.speed = speed;
  }

  public void setTarget(Player player, Player target) {
    resetFollowRequests(player.getUniqueId());
    if (target == null) {
      this.followTargets.remove(player.getUniqueId());
    } else {
      this.followTargets.put(player.getUniqueId(), target.getUniqueId());
    }
  }

  public MobConfig getMobConfig() {
    return (MobConfig) getConfig();
  }

  public void spawn(Player sender, EntityType type, int amount, boolean canDie) {
    for (int i = 0; i < amount; i++) {
      spawn(sender, type, canDie);
    }
  }

  public void spawn(Player sender, EntityType type, boolean canDie) {
    Entity entity = sender.getLocation().getWorld().spawnEntity(sender.getLocation(), type);

    if (entity instanceof LivingEntity mob) {
      if (!canDie) {
        mob.setMaxHealth(Integer.MAX_VALUE);
        mob.setHealth(mob.getMaxHealth());
      }
      mob.setMetadata(
          OWNER_METADATA, new FixedMetadataValue(Community.get(), sender.getUniqueId()));
      trackMob(mob);
    }
  }

  public List<LivingEntity> getOwnedMobs(Player sender) {
    Map<UUID, LivingEntity> mobs = ownedMobs.get(sender.getUniqueId());
    if (mobs == null) return new ArrayList<>();

    mobs.values().removeIf(mob -> {
      if (!mob.isDead() && mob.isValid()) return false;
      followController.remove(mob.getUniqueId());
      return true;
    });
    if (mobs.isEmpty()) ownedMobs.remove(sender.getUniqueId());

    List<LivingEntity> result = new ArrayList<>();
    for (LivingEntity mob : mobs.values()) {
      if (mob.getWorld().equals(sender.getWorld())) result.add(mob);
    }
    return result;
  }

  public int tphere(Player sender) {
    List<LivingEntity> mobs = getOwnedMobs(sender);
    mobs.forEach(mob -> mob.teleport(sender));
    return mobs.size();
  }

  public int heal(Player sender) {
    List<LivingEntity> mobs = getOwnedMobs(sender);
    mobs.forEach(mob -> mob.setHealth(mob.getMaxHealth()));
    return mobs.size();
  }

  public int remove(Player sender) {
    List<LivingEntity> mobs = getOwnedMobs(sender);
    mobs.forEach(mob -> {
      untrackMob(mob);
      mob.remove();
    });
    return mobs.size();
  }

  public boolean toggleFollow(Player sender) {
    UUID playerId = sender.getUniqueId();
    resetFollowRequests(playerId);

    // Unset attackers when switching to follow
    this.attackers.remove(playerId);

    if (this.followTargets.containsKey(playerId)) {
      this.followTargets.remove(playerId);
    } else {
      this.followTargets.put(playerId, playerId);
    }

    return this.followTargets.containsKey(playerId);
  }

  public void attack(Player sender, Player target) {
    setTarget(sender, target);
    this.attackers.add(sender.getUniqueId());
  }

  public boolean toggleAutoAttack(Player sender) {
    UUID playerId = sender.getUniqueId();
    resetFollowRequests(playerId);

    if (this.attackers.contains(sender.getUniqueId())) {
      this.attackers.remove(playerId);
    } else {
      this.attackers.add(playerId);
    }
    return attackers.contains(playerId);
  }

  private void resetFollowRequests(UUID ownerId) {
    Map<UUID, LivingEntity> mobs = ownedMobs.get(ownerId);
    if (mobs != null) mobs.keySet().forEach(followController::remove);
  }
}
