package com.github.cinnamondev.dpt;

import com.github.cinnamondev.dpt.client.PTClient;
import com.github.cinnamondev.dpt.client.PTServer;
import com.github.cinnamondev.dpt.client.PowerAction;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.scheduler.Scheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class VelocityPTServer extends PTServer {
    private final Dpt dpt;
    private final RegisteredServer server;

    private final int maxInactivityTime;
    private boolean ignoreInactivity = false;
    private int minutesIdle;

    public VelocityPTServer(PTClient client, Dpt dpt, RegisteredServer server, String ptUUID, int maxInactivity) {
        super(client, ptUUID);
        this.server = server;
        this.maxInactivityTime = maxInactivity;
        this.dpt = dpt;
    }

    /**
     * Get the name of the server (as registered /w the proxy)
     * @return server name
     */
    public String name() { return server.getServerInfo().getName(); }

    /**
     * Attempts to ping the server. Calls `RegisteredServer::ping`
     * @return ServerPing as a Future.
     */
    public CompletableFuture<ServerPing> ping() {
        return server.ping();
    }

    public void setIgnoreInactivity(boolean ignoreInactivity) {
        this.ignoreInactivity = ignoreInactivity;
    }

    /**
     * Tries to ping the server and returns true if it succeeded.
     *
     * @note Don't bother checking if the server is pingable before pinging. Use `VelocityPTServer::ping` and
     * handle it yourself. Use this function if you don't want to handle a CompletableFuture and/or do not care about
     * the resulting ping.
     *
     * @return server is pingable
     */
    public boolean pingable() {
        AtomicBoolean pingable = new AtomicBoolean(false);
        CompletableFuture<Void> future = server.ping()
                .exceptionally(e -> {
                    getLogger().error("ping no work :(");
                    pingable.set(false);
                    return null;
                }).thenAccept(ping -> {
                    if (ping != null) {
                        getLogger().info("pinged!");
                        pingable.set(true);
                    }
                });
        future.join(); // gotta wait!!
        return pingable.get();
    }

    public boolean checkIfEmpty() {
        return server.getPlayersConnected().isEmpty();
    }

    /**
     * To be called externally, increments interval variable, then stops the server once exceeded.
     * @param interval
     * @return
     */
    public boolean inactivityHandler(int interval) {
        if (maxInactivityTime <= 0) { ignoreInactivity = true; return false;}
        if (!server.getPlayersConnected().isEmpty() || ignoreInactivity) {
            minutesIdle = 0;
            return false;
        }

        if ((minutesIdle += interval) > maxInactivityTime) {
            getLogger().warn("Server {} is shutting down due to inactivity!", name());
            minutesIdle = 0;
            power(PowerAction.STOP);
            return true;
        }
        return false;
    }

    public void feed() {
        minutesIdle = 0;
    }
    /**
     * (non-blocking) asynchronously wait for the server to be ready, then execute `onPing` (or `onTimeout` if it fails)
     *
     * @note If you need more granularity, use `VelocityPTServer:ping`, or `VelocityPTServer::pingable` for a true/false.
     *
     * @param interval How often to check if the server is ready (ms)
     * @param timeout How long to wait before failing (ms)
     * @param startDelay How long to wait before starting to check (minutes)
     * @param onPing function to run when a ping succeeds
     * @param onTimeout function to run when pinging has failed (timed out or some exception (not captured)
     */
    public void onReadyOrTimeout(long timeout, long interval, long startDelay, Consumer<VelocityPTServer> onPing, Runnable onTimeout) {
        AtomicBoolean isFirst = new AtomicBoolean(true);
        AtomicLong timeElapsed = new AtomicLong(0);
        Scheduler.TaskBuilder t = dpt.getProxy().getScheduler()
                .buildTask(dpt, task -> {
                    if (isFirst.get()) {
                        isFirst.set(false);
                        if (!online()) {
                            if (!power(PowerAction.START)) { // we should tell a user when this fails :(
                                onTimeout.run();
                                task.cancel();
                                return;
                            }
                        }
                    }

                    ping().thenAccept(p -> {
                        getLogger().info("Pong! {}", server.getServerInfo().getName());
                        onPing.accept(this);
                        task.cancel();
                    }).whenComplete((p,e) -> {
                        // exceptions can be ignored as assuming they are 'nonpingable'
                        if ((timeout > 0) && timeElapsed.addAndGet(interval) > timeout) {
                            onTimeout.run();
                            task.cancel();
                        }
                    });
                    if ((timeout > 0) && (timeElapsed.addAndGet((int) interval) > timeout)) { onTimeout.run(); task.cancel(); }
                })
                .repeat(interval, TimeUnit.MILLISECONDS);

        if (startDelay > 0) {
            t = t.delay(startDelay, TimeUnit.MINUTES);
        }
        t.schedule();
    }

    /**
     * Start the server (after specified `startDelay`), then send specified `players` (or send a confirmation) when
     * the server has fully started. Does not block.
     *
     * @note If a custom implementation is required on ready, use `VelocityPTServer::onReadyOrTimeout`.
     *
     * @param caller Caller of command
     * @param players Players to send to server
     * @param timeout Maximum time to wait for the server to be in a ready state (milliseconds)
     * @param interval Interval between pings of the server (milliseconds)
     * @param startDelay Initial delay (MINUTES)
     * @param confirm Whether to send players confirmation messages or not.
     */
    public void startThenSend(CommandSource caller,
                              Collection<Player> players,
                              long timeout,
                              long interval,
                              long startDelay,
                              boolean confirm) {

        onReadyOrTimeout(timeout, interval, startDelay, s -> {
            if (confirm) { // send message to player when the server is ready
                players.forEach(p -> p.sendMessage(
                        Component.text("The server \"")
                                .append(Component.text(name()))
                                .append(Component.text("\" is now ready to join! "))
                                .append(
                                        Component.text("[Click here]", NamedTextColor.DARK_PURPLE)
                                                .decorate(TextDecoration.BOLD)
                                                .hoverEvent(HoverEvent.showText(
                                                        Component.text("Click here to join server!")
                                                        )
                                                )
                                )
                                .clickEvent(ClickEvent.runCommand("/server " + name()))
                ));
            } else {
                s.send(players);
            }
        }, () -> { // server did not start or cannot be communicated with via proxy.
            getLogger().error("Exceeded timeout window while starting server {}.", name());
            caller.sendMessage(Component.text(
                    "Could not start server " + name() + "! Contact your administrator"
            ));
        });
    }

    /**
     * Send players in collection to the server. Does not care if players are able to access the server.
     * @param players Players to send
     */
    public void send(Collection<Player> players) {
        Iterator<Player> playerIterator = players.iterator();
        dpt.getProxy().getScheduler().buildTask(dpt, task -> {
            if (!playerIterator.hasNext()) { task.cancel(); return; }
            ConnectionRequestBuilder request = playerIterator.next().createConnectionRequest(server);
            request.fireAndForget();
        }).repeat(50L, TimeUnit.MILLISECONDS).schedule(); // connections need to be staggered to stop us
                                                            // overwhelming the proxy.
    }
}
