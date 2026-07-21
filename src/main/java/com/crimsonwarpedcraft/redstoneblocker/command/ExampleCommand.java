package com.crimsonwarpedcraft.redstoneblocker.command;

import com.crimsonwarpedcraft.cwcommons.command.BaseCommand;
import com.crimsonwarpedcraft.cwcommons.store.bukkit.PlayerDataManager;
import com.crimsonwarpedcraft.redstoneblocker.config.PluginConfig;
import com.crimsonwarpedcraft.redstoneblocker.data.PlayerData;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import org.bukkit.plugin.Plugin;
import com.crimsonwarpedcraft.redstoneblocker.RedstoneBlocker;

/**
 * Registers and handles the /example command.
 *
 * @author Copyright (c) Levi Muniz. All Rights Reserved.
 */
public class ExampleCommand extends BaseCommand {
  /**
   * Creates an ExampleCommand configured by the given plugin configuration.
   *
   * <p>Subcommands:
   *
   * <ul>
   *   <li>/example ping — replies with the configured pong message
   *   <li>/example greet &lt;player&gt; — greets the named player
   * </ul>
   *
   * @param config the plugin configuration
   * @param plugin the owning plugin, used to reschedule onto the main thread
   */
    public ExampleCommand(
        PluginConfig config, Plugin plugin) {
        super(
            new CommandAPICommand("rb")
                .withPermission("redstoneblocker.set")
                .withSubcommand(
                    new CommandAPICommand("allowall")
                    .executes((sender, args) -> {
                        ((RedstoneBlocker) plugin).setAllRedstoneEnabled(
                            !((RedstoneBlocker) plugin).isAllRedstoneEnabled());
                        sender.sendMessage("Redstone is now " +
                            (((RedstoneBlocker) plugin).isAllRedstoneEnabled() ? "enabled" : "disabled") + ".");
                    })
                )
        );
    }

    @Override
    public ExampleCommand register() {
        super.register();

        return this;
    }
}
