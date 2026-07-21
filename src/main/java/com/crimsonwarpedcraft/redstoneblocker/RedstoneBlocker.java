package com.crimsonwarpedcraft.redstoneblocker;

import com.crimsonwarpedcraft.cwcommons.config.bukkit.BukkitConfigManagerBuilder;
import com.crimsonwarpedcraft.cwcommons.store.DataStore;
import com.crimsonwarpedcraft.cwcommons.store.KeySerializers;
import com.crimsonwarpedcraft.cwcommons.store.Repository;
import com.crimsonwarpedcraft.cwcommons.store.bukkit.AutoFlushTask;
import com.crimsonwarpedcraft.cwcommons.store.bukkit.BukkitDataStoreBuilder;
import com.crimsonwarpedcraft.cwcommons.store.bukkit.PlayerDataManager;
import com.crimsonwarpedcraft.redstoneblocker.command.ExampleCommand;
import com.crimsonwarpedcraft.redstoneblocker.config.PluginConfig;
import com.crimsonwarpedcraft.redstoneblocker.data.PlayerData;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIPaperConfig;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.Listener;

public class RedstoneBlocker extends JavaPlugin implements Listener {
  private DataStore store;
  private BukkitTask autoFlushTask;
  private boolean allRedstoneEnabled = true;

  public boolean isAllRedstoneEnabled() {
    return allRedstoneEnabled;
  }

  public void setAllRedstoneEnabled(boolean value) {
    allRedstoneEnabled = value;
  }

  @Override
  public void onLoad() {
    CommandAPI.onLoad(new CommandAPIPaperConfig(this));
  }

  @Override
  public void onEnable() {
    CommandAPI.onEnable();
    suggestPaper();
    saveDefaultConfig();
    PluginConfig config;

    // Load the configuration settings
    try {
      config = new BukkitConfigManagerBuilder()
          .build()
          .load(new File(getDataFolder(), "config.yml"), PluginConfig.class);
    } catch (IOException | IllegalStateException e) {
      getLogger().severe("Failed to load config: " + e.getMessage());
      getServer().getPluginManager().disablePlugin(this);
      return;
    }

    // Setup persistent storage
    try {
      store = new BukkitDataStoreBuilder(getName(), getDataFolder()).build();
    } catch (IOException e) {
      getLogger().severe("Failed to open data store: " + e.getMessage());
      getServer().getPluginManager().disablePlugin(this);
      return;
    }

    autoFlushTask = AutoFlushTask.builder(store, this).build().start();

    Repository<UUID, PlayerData> playerDataRepository =
        store.repository("player-data", PlayerData.class, KeySerializers.forUuid());

    getServer().getPluginManager().registerEvents(this, this);

    // Set up in-game /example command
    new ExampleCommand(config, this).register();
  }

  @Override
  public void onDisable() {
    if (autoFlushTask != null) {
      autoFlushTask.cancel();
    }

    if (store != null) {
      try {
        store.close();
      } catch (Exception e) {
        getLogger().severe("Failed to close data store: " + e.getMessage());
      }
    }

    CommandAPI.onDisable();
  }

  private void suggestPaper() {
    if (isPaper()) {
      return;
    }

    getLogger().warning(getName() + " recommends using Paper.");
  }

  private boolean isPaper() {
    try {
      Class.forName("io.papermc.paper.ServerBuildInfo");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  @EventHandler
  public void onRedstoneChange(BlockRedstoneEvent event) {
    if (allRedstoneEnabled) return;
    event.setNewCurrent(0);
  }
}
