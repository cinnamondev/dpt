package com.github.cinnamondev.dpt;

import com.github.cinnamondev.dpt.client.PTClient;
import com.github.cinnamondev.dpt.client.PowerAction;
import com.github.cinnamondev.dpt.client.PowerState;
import com.github.cinnamondev.dpt.client.PteroServer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.PingOptions;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class Server implements PteroServer {
    private final Dpt p;

    private final RegisteredServer server;
    private final String uuid;
    private final PTClient client;

    public Server(Dpt p, RegisteredServer server, PTClient ptClient, String uuid, int timeout) {
        this.p = p;
        this.server = server;
        this.client = ptClient;
        this.uuid = uuid;

        // schedule watchdog task
        p.getProxy().getScheduler().buildTask(p, this::watchdog)
                .delay(Duration.ofMinutes(5))
                .repeat(Duration.ofMinutes(timeout))
                .schedule();
    }

    private boolean isWatchdogEnabled = false;
    public void watchdogEnabled(boolean enabled) { isWatchdogEnabled = enabled; }
    public boolean watchdogEnabled() { return isWatchdogEnabled; }
    public RegisteredServer getRegistered() { return this.server; }
    private void watchdog() {
        if (!server.getPlayersConnected().isEmpty() || !isWatchdogEnabled) { return; }

        server.ping(PingOptions.builder().timeout(10, TimeUnit.SECONDS).build()).thenAccept(ping -> {
            // tell online admins we're shutting it down
            p.getLogger().info("Shutting down server " + server.getServerInfo().getName() + " due to inactivity.");
            p.getProxy().getAllPlayers().stream().filter(player -> player.hasPermission("dpt.watchdog"))
                    .forEach(player -> player.sendMessage(Component.text("Shutting down server ")
                            .append(pingComponent(ping))
                            .append(Component.text(" due to inactivity."))
                    ));
            power(PowerAction.STOP);
        });
    }

    private Component pingComponent(ServerPing ping) {
        Component playerCount = ping.getPlayers()
                .map(players -> Component.text(players.getOnline() + "/" + players.getMax() + " online"))
                .orElse(Component.text("(Player count hidden)"));

        return Component.text(server.getServerInfo().getName())
                .hoverEvent(
                        ping.getDescriptionComponent().appendNewline()
                                .append(Component.text("Identifier: " + uuid.substring(0,8))).appendNewline()
                                .append(playerCount)
                );
    }

    @Override
    public void power(PowerAction action) { client.power(uuid, action); }

    @Override
    public CompletableFuture<Void> startup(int interval, int timeout, TimeUnit unit) {
        AtomicInteger counter = new AtomicInteger();
        CompletableFuture<Void> future = new CompletableFuture<>();
        power(PowerAction.START);

        var taskFuture = p.getExecutor().schedule(() -> {
            if (counter.getAndAdd(interval) > timeout) { future.completeExceptionally(new TimeoutException("server startup wait timeout")); };
            if (power() == PowerState.RUNNING) { future.complete(null); }
        }, interval, unit);
        future.whenComplete((result, throwable) -> taskFuture.cancel(true));
        return future;
    }

    @Override
    public CompletableFuture<Void> startup() {
        return startup(1, p.startupTimeout(), TimeUnit.SECONDS);
    }

    @Override
    public CompletableFuture<Void> shutdown(int interval, int timeout, TimeUnit unit) {
        AtomicInteger counter = new AtomicInteger();
        CompletableFuture<Void> future = new CompletableFuture<>();
        power(PowerAction.STOP);

        var taskFuture = p.getExecutor().schedule(() -> {
            if (counter.getAndAdd(interval) > timeout) { future.completeExceptionally(new TimeoutException("server startup wait timeout")); };
            if (power() == PowerState.OFFLINE) { future.complete(null); }
        }, interval, unit);
        future.whenComplete((result, throwable) -> taskFuture.cancel(true));
        return future;
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return shutdown(1, p.startupTimeout(), TimeUnit.SECONDS);
    }

    @Override
    public PowerState power() {
        return client.power(uuid);
    }


}
