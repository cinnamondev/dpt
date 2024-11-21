package com.github.cinnamondev.dpt;

import com.github.cinnamondev.dpt.client.PTClient;
import com.github.cinnamondev.dpt.commands.SendCommand;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

@Plugin(id = "dpt",
        name = "dpt",
        description = "dynamically start servers via pterodactyl api",
        version = "1.1-SNAPSHOT")
public class Dpt {
    private YamlConfigurationLoader loader;
    private ConfigurationNode rootNode;

    private HashMap<String, VelocityPTServer> servers;
    public Optional<VelocityPTServer> getServer(String registeredName) {
        return Optional.ofNullable(servers.get(registeredName));
    }

    public void forEachServer(BiConsumer<String, VelocityPTServer> consumer) {
        servers.forEach(consumer);
    }


    private PTClient panelClient;
    private String panelApiKey;
    private String panelUrl;

    private ConfirmMode confirmMode;
    private int startupTimeout;
    public ConfirmMode getConfirmMode() { return this.confirmMode; }
    public int getStartupTimeout() { return this.startupTimeout; }

    private Path configPath;

    public PTClient getPanelClient() {
        return panelClient;
    }

    private final Logger logger;
    public Logger getLogger() { return this.logger;}
    private final ProxyServer proxy;
    public ProxyServer getProxy() { return this.proxy; }

    @Inject
    public Dpt(Logger logger, ProxyServer proxy, @DataDirectory Path dataDirectory) {
        this.logger = logger;
        this.proxy = proxy;
        this.configPath = dataDirectory.resolve("config.yaml");
    }
    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) throws ConfigurateException {
        this.servers = new HashMap<>();

        // CONFIGURATION BRINGUP
        loader = YamlConfigurationLoader.builder()
                .path(configPath)
                .build();
        rootNode = loader.load();

        boolean continueSetup = configuration();
        loader.save(rootNode);
        if (!continueSetup) {
            logger.error("Plugin bring up will not continue, due to irrecoverable errors.");
            return;
        }

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
    }

    /**
     * Brings up configuration file information `panelApiKey`, `panelUrl`, `confirmMode` and searches the config server
     * list to provide a list of servers both in the list and registered on the proxy. invalid servers will be ignored.
     *
     * If no servers are provided initially, the config will be considered invalid and an example will be provided in
     * the configuration file.
     *
     * @return `true` if configuration state is valid.
     */
    private boolean configuration() {
        boolean continueSetup = true;

        ConfigurationNode clientNode = rootNode.node("pterodactyl");
        panelApiKey = clientNode.node("apiKey").getString();
        panelUrl = clientNode.node("domain").getString();
        if (panelApiKey == null || panelUrl == null) { // server cannot run without these key items.
            logger.error("No panel configuration could be found -- please fix this before next startup.");
            continueSetup = false;
            clientNode.node("apiKey").getString("apiKeyHere");
            clientNode.node("domain").getString("https://panel.example.com");
        }
        // create client
        this.panelClient = new PTClient(panelApiKey, panelUrl, logger);
        ConfigurationNode serverListNode = rootNode.node("servers");

        Map<Object, ? extends ConfigurationNode> map = serverListNode.childrenMap(); // explore "key: Node" pairs for
        // each server.
        if (map.isEmpty()) {
            logger.error("No servers could be found in the configuration - defaults have been inserted for you.");
            serverListNode.node("survival-example")
                    .node("uuid")
                    .getString("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx");
            serverListNode.node("survival-example")
                    .node("timeout")
                    .getLong(10);
            serverListNode.node("creative-example")
                    .node("uuid")
                    .getString("fullpterodactyluuidhere");
            serverListNode.node("creative-example")
                    .node("timeout")
                    .getLong(0);
            continueSetup = false;
        } else {
            map.forEach((keyObj, value) -> {
                String registeredName = keyObj.toString();
                String uuid = value.node("uuid").getString();
                long timeout = value.node("timeout").getLong(0);

                // search for server in proxy list.
                Optional<RegisteredServer> server = proxy.getAllServers().stream()
                        .filter(s -> s.getServerInfo().getName().equals(registeredName))
                        .findFirst();
                if (server.isPresent()) {
                    logger.info("found match for " + registeredName);
                    VelocityPTServer ptServer = new VelocityPTServer(panelClient, proxy, server.get(), uuid, timeout);
                    servers.put(registeredName, ptServer);
                } else {
                    logger.warn("Could not find registered server for {}, skipping.", registeredName);
                }

            });
            if (servers.isEmpty()) {
                logger.error("No servers could be found? Names must match the velocity configuration!");
                continueSetup = false;
            }
        }

        // option node options are somewhat non-breaking, if they were not specified we can insert some defaults.
        ConfigurationNode optionsNode = rootNode.node("options");
        this.confirmMode = confirmMode.fromString(optionsNode.node("confirmationMode").getString("never"));
        this.startupTimeout = optionsNode.node("startupTimeout").getInt(5);

        return continueSetup;
    }

    public enum ConfirmMode {
        NEVER("never"),  // never send confirm
        MASS("mass"), // send user confirm to SENDER if they use /dptsend all/here <server>
        PERSONAL("personal"), // send user confirm if they use /dptsend <server>
        ALWAYS("always") // always send user confirm to individuals
        ;

        private final String modeString;
        private ConfirmMode(String modeString) {
            this.modeString = modeString;
        }

        @Override
        public String toString() {
            return modeString;
        }

        public static ConfirmMode fromString(String mode) {
            return switch (mode.toLowerCase()) {
                case "never"    -> NEVER;
                case "mass"     -> MASS;
                case "personal" -> PERSONAL;
                case "always"   -> ALWAYS;
                default        -> NEVER;
            };
        }
    }
}
