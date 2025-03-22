package com.github.cinnamondev.dpt;

import com.github.cinnamondev.dpt.client.PTClient;
import com.github.cinnamondev.dpt.client.PowerAction;
import com.github.cinnamondev.dpt.commands.SendCommand;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;

@Plugin(id = "dpt",
        name = "dpt",
        description = "dynamically start servers via pterodactyl api",
        version = "1.2b")
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
                configFile.createNewFile();
                if (in != null) {
                    try (FileOutputStream out = new FileOutputStream(configFile)) { out.write(in.readAllBytes()); }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        // CONFIGURATION BRINGUP
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(dataDirectory.resolve("config.yaml"))
                .build();
        rootNode = loader.load();

        String apiString = rootNode.node("apiKey").getString(null);
        String urlString = rootNode.node("panelUrl").getString(null);
        if (apiString == null || urlString == null) { throw new RuntimeException("apiKey and url are missing"); }
        panelClient = new PTClient(URI.create(urlString), apiString);

        defaultWatchdogTimeout = rootNode.node("defaultTimeout").getInt(120);
        startupTimeout = rootNode.node("startupTimeout").getInt(120);

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
        
        proxy.getChannelRegistrar().register(IDENTIFIER);
    }

    private void discoverServers() {
        rootNode.node("servers").childrenMap().forEach((keyStr, node) -> {
            String key = (String) keyStr;
            getProxy().getServer(key).ifPresentOrElse(server -> {
                String identifier = node.node("identifier").getString();
                int timeout = node.node("timeout").getInt(defaultWatchdogTimeout);
                servers.put(key, new Server(this, server, panelClient, identifier, timeout));
            }, () -> logger.warn("Server '" + key + "' doesn't seem to be a server listed in velocity :<"));
        });
    }
}
