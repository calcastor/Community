package dev.pgm.community.nick.feature;

import dev.pgm.community.feature.Feature;
import dev.pgm.community.nick.Nick;
import dev.pgm.community.nick.data.NickSelection;
import dev.pgm.community.nick.skin.SkinManager;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;

public interface NickFeature extends Feature {

  CompletableFuture<Nick> getNick(UUID playerId); // Get the CURRENT nick or NULL of a player

  CompletableFuture<Boolean> setNick(UUID playerId, String nickName); // Set the player's Nickname

  CompletableFuture<Boolean> clearNick(UUID playerId); // Remove the player's Nickname

  CompletableFuture<Boolean> toggleNickStatus(UUID playerId); // FALSE IF NO VALID NICK IS SET

  CompletableFuture<Boolean> setNickStatus(UUID playerId, boolean enabled);

  CompletableFuture<Boolean> isNameAvailable(String nickName); // RETURNS TRUE IF NAME IS NOT TAKEN

  boolean isNicked(UUID playerId); // Whether the given playerID is online & had a nickname

  String getOnlineNick(UUID playerId); // Get the nickname of an online player

  Player getPlayerFromNick(String nickName);

  void removeOnlineNick(UUID playerId); // Removes the player from being nicked

  boolean isAutoNicked(
      UUID playerId); // Whether the player was nicked via logging in with nick.<domain>

  SkinManager getSkinManager();

  CompletableFuture<NickSelection> getNickSelection(UUID playerId);
}
