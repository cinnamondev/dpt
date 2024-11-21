package com.github.cinnamondev.dpt;

import com.github.cinnamondev.dpt.client.PTClient;
import com.github.cinnamondev.dpt.client.PTServer;
import com.github.cinnamondev.dpt.client.PowerAction;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class VelocityPTServer extends PTServer {
    private final ProxyServer proxy;
    private final RegisteredServer server;

    private final long inactivityTimeout;
    private final AtomicBoolean inactivityHandlerActive = new AtomicBoolean(false);
    private ScheduledTask inactivityHandler;

    public VelocityPTServer(PTClient client, ProxyServer proxy, RegisteredServer server, String ptUUID, long inactivityTimeout) {
        super(client, ptUUID);
        this.proxy = proxy;
        this.server = server;
        this.inactivityTimeout = inactivityTimeout;
        startInactivityHandler();
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
     * Inactivity handler. Starts on initialization, stops if the server is empty (and stops the server), or if the
     * server is offline. Implementations of commands that start servers should call this if they want to make use.
     *
     * @note If the server is started via other means, this plugin will not check and the server will remain online
     * until intervened.
     */
    public void startInactivityHandler() {
        if (inactivityTimeout <= 0) { return; }
        inactivityHandlerActive.set(true);
        this.inactivityHandler = proxy.getScheduler()
                .buildTask(this, task -> {
                    // stop if server is offline or the inactivity handler handle got changed.
                    if (!pingable() || inactivityHandlerActive.get()) {
                        inactivityHandlerActive.set(false);
                        task.cancel();
                        return;
                    }
                    if (server.getPlayersConnected().isEmpty()) {
                        Scheduler scheduler = proxy.getScheduler();
                        getLogger().info("Stopping server {} as it has been inactive for too long.", name());
                        power(PowerAction.STOP);
                        inactivityHandlerActive.set(false);
                        task.cancel();
                    }
                }).repeat(inactivityTimeout, TimeUnit.MINUTES)
                .delay(inactivityTimeout,TimeUnit.MINUTES)
                .schedule();
    }

    public void stopInactivityHandler() {
        inactivityHandler.cancel();
        inactivityHandlerActive.set(false);
    }
    /**
     * (non-blocking) asynchronously wait for the server to be ready, then execute `onPing` (or `onTimeout` if it fails)
     *
     * @note If you need more granularity, use `VelocityPTServer:ping`, or `VelocityPTServer::pingable` for a true/false.
     *
     * @param interval How often to check if the server is ready (ms)
     * @param timeout How long to wait before failing (ms)
     * @param startDelay How long to wait before starting to check (minutes) (used for
     * @param onPing function to run when a ping succeeds
     * @param onTimeout function to run when pinging has failed (timed out or some exception (not captured)
     */
    public void onReadyOrTimeout(long timeout, long interval, long startDelay, Consumer<VelocityPTServer> onPing, Runnable onTimeout) {
        AtomicLong timeElapsed = new AtomicLong(0);
        Scheduler.TaskBuilder t = proxy.getScheduler()
                .buildTask(this, task -> {
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
            t = t.delay(startDelay, TimeUnit.MILLISECONDS);
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

        if (!online()) { power(PowerAction.START); }
        onReadyOrTimeout(timeout, interval, startDelay, s -> {
            s.startInactivityHandler(); // timeout feature.
            if (confirm) { // send message to player when the server is ready
                players.forEach(p -> p.sendMessage(
                        Component.text("The server" + name() + "is now ready to join!")
                                .append(Component.text("[Click here]")
                                        // use the default whotsit
                                        .clickEvent(ClickEvent.runCommand("/server " + name())
                                        )
                                )
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
        players.forEach(p -> {
            ConnectionRequestBuilder request = p.createConnectionRequest(server);
            request.connectWithIndication();
        });
    }
}
