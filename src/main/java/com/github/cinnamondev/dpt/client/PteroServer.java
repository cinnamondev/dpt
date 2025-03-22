package com.github.cinnamondev.dpt.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public interface PteroServer {
    void power(PowerAction action);
    default void start() { power(PowerAction.START); }

    CompletableFuture<Void> startup(int interval, int timeout, TimeUnit unit);
    CompletableFuture<Void> startup();
    CompletableFuture<Void> shutdown(int interval, int timeout, TimeUnit unit);
    CompletableFuture<Void> shutdown();

    default void stop() { power(PowerAction.STOP); }
    default void restart() { power(PowerAction.RESTART); }
    default void kill() { power(PowerAction.KILL); }
    PowerState power();
    default boolean online() { return power() == PowerState.RUNNING; }
    default boolean offline() { return power() == PowerState.OFFLINE; }
}
