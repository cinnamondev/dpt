package com.github.cinnamondev.dpt;

import com.github.cinnamondev.dpt.client.PTClient;
import com.github.cinnamondev.dpt.client.PowerAction;
import com.github.cinnamondev.dpt.client.PowerState;
import com.github.cinnamondev.dpt.commands.HelloWorld;
import com.github.cinnamondev.dpt.commands.SendCommand;
import com.github.cinnamondev.dpt.util.ConfirmMode;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.Scheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

@Plugin(id = "dpt", name = "dpt", version = "1.0-SNAPSHOT")
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
    public ConfirmMode getConfirmMode() { return this.confirmMode; }

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
        //if (!continueSetup) { return; }

        registerCommands();
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
            clientNode.node("apiKey").getString("apiKeyHere");
            clientNode.node("domain").getString("https://panel.example.com");
        }
        // create client
        this.panelClient = new PTClient(panelApiKey, panelUrl, logger);
        ConfigurationNode serverListNode = rootNode.node("servers");

        Map<Object, ? extends ConfigurationNode> map = serverListNode.childrenMap(); // explore "key: Node" pairs for
        // each server.
        if (map.isEmpty()) {
            logger.error("inserting default value for servers, please change.");
            serverListNode.node("survival-example")
                    .node("uuid")
                    .getString("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx");
            serverListNode.node("creative-example")
                    .node("uuid")
                    .getString("fullpterodactyluuidhere");
            continueSetup = false;
        } else {
            map.forEach((keyObj, value) -> {
                String registeredName = keyObj.toString();
                String uuid = value.node("uuid").getString();

                // search for server in proxy list.
                Optional<RegisteredServer> server = proxy.getAllServers().stream()
                        .filter(s -> s.getServerInfo().getName().equals(registeredName))
                        .findFirst();
                if (server.isPresent()) {
                    logger.info("found match for " + registeredName);
                    VelocityPTServer ptServer = new VelocityPTServer(panelClient, proxy, server.get(), uuid);
                    servers.put(registeredName, ptServer);
                } else {
                    logger.warn("Could not find registered server for {}, skipping.", registeredName);
                }

            });
            if (servers.isEmpty()) { continueSetup = false; }
        }

        ConfigurationNode optionsNode = rootNode.node("options");
        this.confirmMode = confirmMode.fromString(optionsNode.node("confirmationMode").getString("never"));


        return continueSetup;
    }


    public void registerCommands() {
        CommandManager commandManager = proxy.getCommandManager();

        CommandMeta metaHello = commandManager.metaBuilder("dpthello")
                .aliases("helloworld")
                .plugin(this)
                .build();

        commandManager.register(metaHello, HelloWorld.createBrigadierCommand(this));

        CommandMeta metaSend = commandManager.metaBuilder("dptsend")
                .aliases("dsend")
                .plugin(this)
                .build();

        commandManager.register(metaSend, SendCommand.sendCommand(this));

    }

    public Scheduler.TaskBuilder sendPlayerTask(CommandSource caller,
                                                        VelocityPTServer server,
                                                        String serverName,
                                                        Collection<Player> players,
                                                        boolean confirm,
                                                        long timeTimeout,
                                                        long retryDelay) {
        AtomicInteger counter = new AtomicInteger(0);
        Scheduler.TaskBuilder sendPlayerTask = proxy.getScheduler()
                .buildTask(this, task -> {
                    if (server.ready()) {
                        // send each user a message to confirm sending.
                        if (confirm) {
                            players.forEach(player -> player.sendMessage(
                                    Component.text("The server" + serverName + "is now ready to join!")
                                            .append(Component.text("[Click here]")
                                                    .clickEvent(ClickEvent.runCommand("/server " + serverName)
                                                    )
                                            )
                                    )
                            );
                        } else {
                            server.send(players);
                        }
                        task.cancel();
                    } else {
                        logger.info("Server {} is not ready yet. Waiting...", serverName);
                        int time = counter.addAndGet((int) retryDelay);
                        if (time > timeTimeout) {
                            logger.warn("Was not able to start server {} within time allocated.", serverName);
                            caller.sendMessage(Component.text("Tried to start server" + serverName + ", but it took too long! (see server console)"));
                            task.cancel();
                        }
                    }
                });

        if (retryDelay > 0) {
            sendPlayerTask = sendPlayerTask.repeat(retryDelay, TimeUnit.MILLISECONDS);
        }
        return sendPlayerTask;
    }

    /**
     * Send player task with default timeout and delay.
     *
     * Timeout = 300 s (5 minutes)
     * Repeat delay = 5 seconds
     *
     * @param caller
     * @param server
     * @param serverName
     * @param players
     * @param confirm
     * @return
     */
    public Scheduler.TaskBuilder sendPlayerTask(CommandSource caller,
                                                        VelocityPTServer server,
                                                        String serverName,
                                                        Collection<Player> players,
                                                        boolean confirm) {
        return sendPlayerTask(caller, server, serverName, players, confirm, 300000, 5000);
    }
}
