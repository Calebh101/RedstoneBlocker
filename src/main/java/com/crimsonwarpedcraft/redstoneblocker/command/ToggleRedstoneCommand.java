package com.crimsonwarpedcraft.redstoneblocker.command;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.executors.CommandExecutor;
import org.bukkit.entity.Player;
import com.crimsonwarpedcraft.redstoneblocker.RedstoneBlocker;

public class ToggleRedstoneCommand {
  private final RedstoneBlocker plugin;

  public ToggleRedstoneCommand(RedstoneBlocker plugin) {
    this.plugin = plugin;
  }

  public void register() {
    new CommandAPICommand("allowall")
        .withPermission("redstoneblocker.set")
        .executes((sender, args) -> {
          plugin.setAllRedstoneEnabled(!plugin.isAllRedstoneEnabled());
          sender.sendMessage("Redstone is now " +
              (plugin.isAllRedstoneEnabled() ? "enabled" : "disabled") + ".");
        })
        .register();
  }
}