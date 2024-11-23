package com.github.cinnamondev.dpt;

import com.velocitypowered.api.scheduler.ScheduledTask;

import java.sql.Time;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class InactivityWatchdog {
    private Dpt plugin;
    private ScheduledTask task;
    private AtomicBoolean active;
    private static final int INTERVAL_MINS = 1;

    public InactivityWatchdog(Dpt plugin) {
        this.plugin = plugin;
    }
    public void start() {
        task = plugin.getProxy().getScheduler()
                .buildTask(plugin,this::inactivityChecker)
                .delay(INTERVAL_MINS, TimeUnit.MINUTES)
                .repeat(INTERVAL_MINS,TimeUnit.MINUTES)
                .schedule();
    }
    private void inactivityChecker() {
        plugin.forEachServer((k,s) -> {
            s.inactivityHandler(INTERVAL_MINS);
        });
    }
    public void close() {
        task.cancel();
    }
}
