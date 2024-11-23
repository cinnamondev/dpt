package com.github.cinnamondev.dpt.commands;

import com.github.cinnamondev.dpt.Dpt;
import com.github.cinnamondev.dpt.VelocityPTServer;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

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
                .requires(src -> src.hasPermission("dpt.send"))
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
                                ).then(BrigadierCommand.requiredArgumentBuilder("delayMinutes", IntegerArgumentType.integer())
                                        .executes(ctx -> commandParser(dpt, ctx,
                                                StringArgumentType.getString(ctx, "player"),
                                                StringArgumentType.getString(ctx, "server"),
                                                IntegerArgumentType.getInteger(ctx, "delayMinutes")))
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
        boolean waitForConfirm = dpt.getConfirmMode().equals(Dpt.ConfirmMode.ALWAYS);

        if (!(ctx.getSource().hasPermission("dpt.send." + serverArgument) || ctx.getSource().hasPermission("dpt.send.any"))) {
            ctx.getSource().sendMessage(
                    Component.text(
                            "You do not have sufficient permission to send players here.",
                            NamedTextColor.RED
                    )
            );
            return 1;
        }

        // parse player argument
        Collection<Player> playersToSend = Collections.emptyList();
        if (playerArgument.equals("all")) { // ALL players on proxy
             playersToSend = proxy.getAllPlayers();
        } else if (playerArgument.equals("here")) { // ALL players on the server *the caller is executing from*
            if (ctx.getSource() instanceof Player p) {
                playersToSend = p.getCurrentServer()
                        .orElseThrow() // we should not expect this to throw, because the player is on a server.
                        .getServer()   // if it throws, seek immediate shelter.
                        .getPlayersConnected();
            } else {
                ctx.getSource().sendMessage(Component.text("`here` can only be used by players"));
            }
        } else {
            Optional<Player> player = proxy.getPlayer(playerArgument);
            if (player.isPresent()) {
                playersToSend = Collections.singleton(player.get());
            } else { // of last resort.
                ctx.getSource().sendMessage(Component.text("`" + playerArgument + "` not found"));
            }
        }

        boolean isSelf = false;
        if (ctx.getSource() instanceof Player p && playerArgument.equals(p.getUsername())) {
            // player is sending themselves (requires no `other`) permission
            isSelf = true;
            waitForConfirm = dpt.getConfirmMode().equals(Dpt.ConfirmMode.PERSONAL);
            playersToSend = Collections.singleton(p);
        }
        // But Nobody Came.
        if (playersToSend.isEmpty()) { return 1; }

        Optional<VelocityPTServer> _server = dpt.getServer(serverArgument);
        if (_server.isPresent()) {
            VelocityPTServer server = _server.get();
            // based on args
            TextComponent delayMsg;
            if (delayTime > 0) {
                delayMsg = Component.text(" in ")
                        .append(Component.text(delayTime, NamedTextColor.BLUE).decorate(TextDecoration.BOLD))
                        .append(Component.text(" minutes."));
            } else {
                delayMsg = Component.text("");
            }
            String confirmMsg = waitForConfirm ? " (You will receive confirmation when it's ready)" : "";
            if (!isSelf) {
                ctx.getSource().sendMessage( // send caller message
                        Component.text("Sending " + playerArgument + " to " + serverArgument + delayMsg)
                );
            }
            playersToSend.forEach(p -> p.sendMessage( // send each player message. if immediate, they prob won't see it.
                    Component.text("You're being sent to: ")
                            .append(Component.text(serverArgument, NamedTextColor.DARK_PURPLE)
                                    .decorate(TextDecoration.BOLD))
                            .append(delayMsg)
                            .append(Component.text(confirmMsg))
                    )
            );
            // this will not block.
            server.startThenSend(ctx.getSource(),
                    playersToSend,
                    READY_TIMEOUT,
                    REPEAT_DELAY,
                    delayTime,
                    waitForConfirm
            );
        } else {
            ctx.getSource().sendMessage(
                    Component.text(
                            "Server" + serverArgument + " not found",
                            NamedTextColor.RED
                    )
            );
        }

        return 1;
    }




}
