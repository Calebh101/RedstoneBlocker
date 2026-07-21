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
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Powerable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RedstoneBlocker extends JavaPlugin implements Listener {
  private static final Set<Material> REDSTONE_TYPES = EnumSet.of(
    Material.REDSTONE_WIRE,
    Material.REDSTONE_TORCH,
    Material.REDSTONE_WALL_TORCH,
    Material.REPEATER,
    Material.COMPARATOR,
    Material.LEVER,
    Material.OBSERVER,
    Material.REDSTONE_LAMP,
    Material.PISTON,
    Material.STICKY_PISTON,
    Material.DISPENSER,
    Material.DROPPER,
    Material.HOPPER,
    Material.POWERED_RAIL,
    Material.DETECTOR_RAIL,
    Material.ACTIVATOR_RAIL
  );

  private DataStore store;
  private BukkitTask autoFlushTask;
  private boolean allRedstoneEnabled = true;

  public Repository<String, Boolean> chunkData;
  public final Set<String> exemptChunks = ConcurrentHashMap.newKeySet();

  public static String chunkKey(Chunk chunk) {
    return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
  }

  public boolean isAllRedstoneEnabled() {
    return allRedstoneEnabled;
  }

  public void setAllRedstoneEnabled(boolean value) {
    allRedstoneEnabled = value;
    if (!value) disableAllRedstone();
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

    Repository<UUID, PlayerData> playerDataRepository = store.repository("player-data", PlayerData.class, KeySerializers.forUuid());
    chunkData = store.repository("chunk-data", Boolean.class, KeySerializers.forString());

    chunkData.getAll().thenAccept(map -> {
      exemptChunks.addAll(map.keySet());
    }).exceptionally(e -> {
      getLogger().severe("Failed to load exempt chunks: " + e.getMessage());
      return null;
    });

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
    Chunk chunk = event.getBlock().getChunk();
    if (exemptChunks.contains(chunkKey(chunk))) return;
    event.setNewCurrent(0);
  }

  @EventHandler
  public void onChunkLoad(ChunkLoadEvent event) {
    if (allRedstoneEnabled) return;
    disableRedstoneInChunk(event.getChunk());
  }

  @EventHandler
  public void onBlockPlace(BlockPlaceEvent event) {
    if (allRedstoneEnabled) return;
    scanBlock(event.getBlock());
  }

  /** Sweeps every currently loaded chunk in every world and kills active redstone. */
  public void disableAllRedstone() {
    for (World world : getServer().getWorlds()) {
      for (Chunk chunk : world.getLoadedChunks()) {
        if (exemptChunks.contains(chunkKey(chunk))) continue;
        disableRedstoneInChunk(chunk);
      }
    }
  }

  /** Kills active redstone state in a single chunk. Skips air above the highest block per column. */
  public void disableRedstoneInChunk(Chunk chunk) {
    World world = chunk.getWorld();
    int minY = world.getMinHeight();

    for (int x = 0; x < 16; x++) {
      for (int z = 0; z < 16; z++) {
        int worldX = (chunk.getX() << 4) + x;
        int worldZ = (chunk.getZ() << 4) + z;

        // Nothing above the highest solid block is worth checking - it's air.
        int topY = world.getHighestBlockYAt(worldX, worldZ);

        for (int y = minY; y <= topY; y++) {
          Block block = chunk.getBlock(x, y, z);
          scanBlock(block);
        }
      }
    }
  }

  public void scanBlock(Block block) {
    Material type = block.getType();
    if (!REDSTONE_TYPES.contains(type)) return;

    BlockData data = block.getBlockData();
    boolean changed = false;

    if (data instanceof Powerable powerable && powerable.isPowered()) {
      powerable.setPowered(false);
      changed = true;
    }
    if (data instanceof Lightable lightable && lightable.isLit()) {
      lightable.setLit(false);
      changed = true;
    }
    if (data instanceof Levelled levelled && levelled.getLevel() > 0) {
      levelled.setLevel(0);
      changed = true;
    }

    if (changed) {
      block.setBlockData(data, false); // false = skip physics update
    }
  }
}