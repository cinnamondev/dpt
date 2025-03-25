package com.github.cinnamondev.dpt;

import com.github.cinnamondev.dpt.client.PTClient;
import com.github.cinnamondev.dpt.commands.PingCommand;
import com.github.cinnamondev.dpt.commands.SendCommand;
import com.github.cinnamondev.dpt.commands.WatchdogCommand;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Plugin(id = "dpt",
        name = "dpt",
        description = "dynamically start servers via pterodactyl api",
        version = "2.0")
public class Dpt {
    private ConfigurationNode rootNode;

    private static final MinecraftChannelIdentifier IDENTIFIER = MinecraftChannelIdentifier.from("dpt:watchdog");

    private final Path dataDirectory;

    private PTClient panelClient;
    public PTClient getPanelClient() {
        return panelClient;
    }

    private final Logger logger;
    public Logger getLogger() { return this.logger;}

    private final ProxyServer proxy;
    public ProxyServer getProxy() { return this.proxy; }

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    public ScheduledExecutorService getExecutor() { return this.executor; }

    private int startupTimeout = 120;
    public int startupTimeout() { return startupTimeout; }
    private int defaultWatchdogTimeout = 120;
    public int defaultWatchdogTimeout() { return defaultWatchdogTimeout; }

    private final HashMap<String, Server> servers = new HashMap<>();
    public Optional<Server> getServer(String registeredName) {
        return Optional.ofNullable(servers.get(registeredName));
    }

    public Map<String,Server> getDptServers() {
        return servers;
    }


    @Inject
    public Dpt(Logger logger, ProxyServer proxy, @DataDirectory Path dataDirectory) {
        this.logger = logger;
        this.proxy = proxy;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) throws ConfigurateException {
        File configFile = dataDirectory.resolve("config.yaml").toFile();
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            try (InputStream in = this.getClass().getClassLoader().getResourceAsStream("config.yaml")) {
                if (in == null) { throw new Exception("Could not find config.yaml resource :("); }
                Files.copy(in, configFile.toPath());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        // CONFIGURATION BRINGUP
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .file(configFile)
                .build();
        rootNode = loader.load();

        String apiString = rootNode.node("api-key").getString();
        String urlString = rootNode.node("api-url").getString();
        if (apiString == null || urlString == null) { throw new RuntimeException("apiKey and url are missing"); }
        panelClient = new PTClient(logger, URI.create(urlString), apiString);

        defaultWatchdogTimeout = rootNode.node("default-timeout").getInt(120);
        startupTimeout = rootNode.node("startup-timeout").getInt(120);

        proxy.getChannelRegistrar().register(IDENTIFIER);
        discoverServers();

        CommandManager commandManager = proxy.getCommandManager();

        CommandMeta metaSend = commandManager.metaBuilder("dptsend")
                .aliases("dsend")
                .plugin(this)
                .build();
        commandManager.register(metaSend, SendCommand.sendCommand(this));

        CommandMeta metaServer = commandManager.metaBuilder("dptserver")
                .aliases("dserver", "ds")
                .plugin(this)
                .build();
        commandManager.register(metaServer, SendCommand.serverCommand(this));

        CommandMeta metaPing = commandManager.metaBuilder("dptping")
                .aliases("dp", "ping")
                .plugin(this)
                .build();
        commandManager.register(metaPing, PingCommand.command(this));

        CommandMeta metaWatchdog = commandManager.metaBuilder("dptwd")
                .aliases("dwd", "watchdog")
                .plugin(this)
                .build();
        commandManager.register(metaWatchdog, WatchdogCommand.command(this));
    }

    private void discoverServers() {
        rootNode.node("servers").childrenMap().forEach((keyStr, node) -> {
            String key = (String) keyStr;
            getProxy().getServer(key).ifPresentOrElse(server -> {
                String identifier = node.node("uuid").getString();
                int timeout = node.node("timeout").getInt(defaultWatchdogTimeout);
                boolean watchdogEnabled = node.node("watchdog").getBoolean(false);
                servers.put(key, new Server(this, server, panelClient, identifier, timeout, watchdogEnabled));
            }, () -> logger.warn("Server '" + key + "' doesn't seem to be a registered server. Ignoring!"));
        });
    }
}
