package com.github.cinnamondev.dpt;

import com.github.cinnamondev.dpt.client.PTClient;
import com.github.cinnamondev.dpt.client.PTServer;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class VelocityPTServer extends PTServer {
    private ProxyServer proxy;
    private RegisteredServer server;

    public VelocityPTServer(PTClient client, ProxyServer proxy, RegisteredServer server, String ptUUID) {
        super(client, ptUUID);
        this.proxy = proxy;
        this.server = server;
    }


    public boolean pingable() {
        AtomicBoolean pingable = new AtomicBoolean(false);
        server.ping().exceptionally(e -> {
            pingable.set(false);
            return null;
        }).thenAccept(ping -> {
           if (ping != null) {
               pingable.set(true);
           }
        });
        return pingable.get();
    }

    public boolean ready() {
        return (online() && pingable());
    }

    public void onReady(long interval, long timeout, Consumer<VelocityPTServer> callback) {
        AtomicLong timeElapsed = new AtomicLong(0);
        proxy.getScheduler()
                .buildTask(this, task -> {
                    if ((timeout != -1) && (timeElapsed.addAndGet((int) interval) >= timeout)) { task.cancel(); }
                    if (ready()) {
                        callback.accept(this);
                        task.cancel();
                    }
                })
                .repeat(timeout, TimeUnit.MINUTES)
                .schedule();
    }

    public void onReady(long interval ,Consumer<VelocityPTServer> callback) {
        onReady(interval, -1, callback);
    }
    public void onReady(Consumer<VelocityPTServer> callback) {
        onReady(100, -1,callback);
    }

    public void send(Player p) {
        send(Collections.singletonList(p));
    }
    public void send(Collection<Player> players) {
        players.forEach(p -> {
            ConnectionRequestBuilder request = p.createConnectionRequest(server);
            request.connectWithIndication();
        });
    }
}
