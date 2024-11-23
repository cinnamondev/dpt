package com.github.cinnamondev.dptCompanion;

import com.google.common.collect.Iterables;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Logger;

public final class DptCompanion extends JavaPlugin {
    private BukkitTask task;
    private BukkitScheduler scheduler = Bukkit.getScheduler();
    private Logger logger = Bukkit.getLogger();
    @Override
    public void onEnable() {
        getServer().getMessenger().registerOutgoingPluginChannel(this, "dpt:watchdog");
        logger.info("dpt watchdog is alive!");
        startTaskTimer();
    }

    public void startTaskTimer() {
        task = scheduler.runTaskTimer(this,
                () -> {
                    ByteArrayDataOutput out = ByteStreams.newDataOutput();
                    out.writeBoolean(true);
                    Player p = Iterables.getFirst(Bukkit.getOnlinePlayers(), null);
                    if (p == null) { return; } // if we cant send messages, the watchdog wont get pet
                                               // the wd will thus eventually bark&bite :)

                    p.sendPluginMessage(this, "dpt:watchdog", out.toByteArray());
                },
                20L*10L,
                20L*5L
        );
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        if (task != null) { task.cancel(); }
        // Plugin shutdown logic
    }


}
