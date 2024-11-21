package com.github.cinnamondev.dpt.commands;

import com.github.cinnamondev.dpt.Dpt;
import com.github.cinnamondev.dpt.VelocityPTServer;
import com.github.cinnamondev.dpt.client.PowerAction;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

public class SendCommand {
    /**
     * How long the plugin should wait before timing out a server request (TODO: configurable)
     */
    private static final long READY_TIMEOUT = 300000;
    /**
     * How long the plugin should wait before pinging the server again
     */
    private static final long REPEAT_DELAY = 5000;

    /**
     * BrigadierCommand for sending other player(s) to a server registered in the plugin.
     * @param dpt Plugin
     * @return BrigadierCommand
     */
    public static BrigadierCommand sendCommand(Dpt dpt) {
        LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("dptsend")
                .requires(src -> src.hasPermission("dpt.send.others"))
                .executes(ctx -> {
                    ctx.getSource().sendMessage(Component.text("/dptsend <username/all/here> <server>"));
                    return 1;
                })
                .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                        .suggests((ctx,builder) -> {
                            builder.suggest("all");
                            if (ctx.getSource() instanceof Player) {
                                builder.suggest("here");
                            }
                            dpt.getProxy().getAllPlayers().stream()
                                    .map(Player::getUsername)
                                    .forEach(builder::suggest);

                            return builder.buildFuture();
                        })
                        .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                                .suggests((ctx,builder) -> {
                                    dpt.forEachServer((key, server) -> {
                                        CommandSource src = ctx.getSource();
                                        if (src.hasPermission("dpt.send.any") || src.hasPermission("dpt.send." + key)) {
                                            builder.suggest(key);
                                        }
                                    });
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> commandParser(dpt, ctx,
                                        StringArgumentType.getString(ctx, "player"),
                                        StringArgumentType.getString(ctx, "server"),
                                        0)
                                ).then(BrigadierCommand.requiredArgumentBuilder("delayMS", IntegerArgumentType.integer())
                                        .executes(ctx -> commandParser(dpt, ctx,
                                                StringArgumentType.getString(ctx, "player"),
                                                StringArgumentType.getString(ctx, "server"),
                                                IntegerArgumentType.getInteger(ctx, "time")))
                                )
                        )
                )
                .build();

        return new BrigadierCommand(node);
    }

    /**
     * BrigadierCommand to send yourself to a server registered in the plugin.
     * @param dpt Plugin
     * @return BrigadierCommand
     */
    public static BrigadierCommand serverCommand(Dpt dpt) {
        LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("dptserver")
                .requires(src -> src.hasPermission("dpt.send"))
                .requires(src -> src instanceof Player)
                .executes(ctx -> {
                    ctx.getSource().sendMessage(Component.text("/dptserver <server>"));
                    return 1;
                })
                .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            dpt.forEachServer((key, server) -> {
                                CommandSource src = ctx.getSource();
                                if (src.hasPermission("dpt.send.any") || src.hasPermission("dpt.send." + key)) {
                                    builder.suggest(key);
                                }
                            });
                            return builder.buildFuture();
                        }).executes(ctx -> commandParser(dpt, ctx,
                                ((Player) ctx.getSource()).getUsername(),
                                StringArgumentType.getString(ctx, "server"),
                                0
                                )
                        )
                ).build();

        return new BrigadierCommand(node);
    }

    /**
     * Command logic. Parses input arguments and sends players to servers.
     *
     * @param dpt Plugin
     * @param ctx Context in which the command was called.
     * @param playerArgument username of player to send (or "all"/"here" to send many players)
     * @param serverArgument name of server to send players to
     * @param delayTime time (in minutes) to wait before sending players (set to 0 for no delay)
     * @return command success (1)
     */
    private static int commandParser(Dpt dpt,
                                     CommandContext<CommandSource> ctx,
                                     String playerArgument,
                                     String serverArgument,
                                     long delayTime) {
        ProxyServer proxy = dpt.getProxy();
        String playerArg = StringArgumentType.getString(ctx, "player");
        String serverArg = StringArgumentType.getString(ctx, "server");
        boolean waitForConfirm = dpt.getConfirmMode().equals(Dpt.ConfirmMode.ALWAYS);

        if (!ctx.getSource().hasPermission("dpt.send." + serverArg) || !ctx.getSource().hasPermission("dpt.send.any")) {
            ctx.getSource().sendMessage(Component.text("You do not have sufficient permission to send players here."));
            return 1;
        }

        // parse player argument
        Collection<Player> playersToSend = Collections.emptyList();
        if (playerArg.equals("all")) { // ALL players on proxy
             playersToSend = proxy.getAllPlayers();
        } else if (playerArg.equals("here")) { // ALL players on the server *the caller is executing from*
            if (ctx.getSource() instanceof Player p) {
                playersToSend = p.getCurrentServer()
                        .orElseThrow() // we should not expect this to throw, because the player is on a server.
                        .getServer()   // if it throws, seek immediate shelter.
                        .getPlayersConnected();
            } else {
                ctx.getSource().sendMessage(Component.text("`here` can only be used by players"));
            }
        } else {
            Optional<Player> player = proxy.getPlayer(playerArg);
            if (player.isPresent()) {
                playersToSend = Collections.singleton(player.get());
            } else { // of last resort.
                ctx.getSource().sendMessage(Component.text("`" + playerArg + "` not found"));
            }
        }


        if (ctx.getSource() instanceof Player p && playerArg.equals(p.getUsername())) {
            // player is sending themselves (requires no `other`) permission
            waitForConfirm = dpt.getConfirmMode().equals(Dpt.ConfirmMode.PERSONAL);
            playersToSend = Collections.singleton(p);
        }
        // But Nobody Came.
        if (playersToSend.isEmpty()) { return 1; }

        Optional<VelocityPTServer> _server = dpt.getServer(serverArg);
        if (_server.isPresent()) {
            VelocityPTServer server = _server.get();
            // based on args
            String delayMsg = delayTime > 0 ? "in " + delayTime + " minutes." : "";
            String confirmMsg = waitForConfirm ? "You will receive a confirmation when it's ready." : "";

            ctx.getSource().sendMessage( // send caller message
                    Component.text("Sending " + playerArg + " to " + serverArg + delayMsg)
            );
            playersToSend.forEach(p -> p.sendMessage( // send each player message. if immediate, they prob won't see it.
                    Component.text("You are going to be sent to" + serverArg + ". " + delayMsg + confirmMsg)
            ));
            // this will not block.
            startThenSend(dpt,
                    ctx.getSource(),
                    server,
                    playersToSend,
                    READY_TIMEOUT,
                    REPEAT_DELAY,
                    delayTime,
                    waitForConfirm
            );
        } else {
            ctx.getSource().sendMessage(Component.text("Server" + serverArg + " not found"));
        }

        return 1;
    }

    /**
     * Start the server (after specified `startDelay`), then send specified `players` (or send a confirmation) when
     * the server has fully started. Does not block.
     *
     * @note If a custom implementation is required on ready, use `VelocityPTServer::onReadyOrTimeout`.
     *
     * @param dpt Plugin
     * @param caller Caller of command
     * @param server Server to send players to
     * @param players Players to send to server
     * @param timeout Maximum time to wait for the server to be in a ready state (milliseconds)
     * @param interval Interval between pings of the server (milliseconds)
     * @param startDelay Initial delay (MINUTES)
     * @param confirm Whether to send players confirmation messages or not.
     */
    public static void startThenSend(Dpt dpt,
                                   CommandSource caller,
                                   VelocityPTServer server,
                                   Collection<Player> players,
                                   long timeout,
                                   long interval,
                                   long startDelay,
                                   boolean confirm) {

        if (!server.online()) { server.power(PowerAction.START); }
        server.onReadyOrTimeout(timeout, interval, startDelay, s -> {
            s.startInactivityHandler(); // timeout feature.
            if (confirm) { // send message to player when the server is ready
                players.forEach(p -> p.sendMessage(
                        Component.text("The server" + server.name() + "is now ready to join!")
                                .append(Component.text("[Click here]")
                                        // use the default whotsit
                                        .clickEvent(ClickEvent.runCommand("/server " + server.name())
                                        )
                                )
                ));
            } else {
                server.send(players);
            }
        }, () -> { // server did not start or cannot be communicated with via proxy.
            dpt.getLogger().error("Exceeded timeout window while starting server {}.", server.name());
            caller.sendMessage(Component.text(
                    "Could not start server " + server.name() + "! Contact your administrator"
            ));
        });
    }


}
