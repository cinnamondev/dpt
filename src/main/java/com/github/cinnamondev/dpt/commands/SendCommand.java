package com.github.cinnamondev.dpt.commands;

import com.github.cinnamondev.dpt.Dpt;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Collection;
import java.util.Collections;

public class SendCommand {
    private enum ConfirmationMode {
        PLAYER,
        SENDER,
        NONE
    }
    /**
     * BrigadierCommand for sending other player(s) to a server registered in the plugin.
     * @param dpt Plugin
     * @return BrigadierCommand
     */
    public static BrigadierCommand sendCommand(Dpt dpt) {
        LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("dptsend")
                .requires(src -> src.hasPermission("dpt.send.others"))
                .executes(SendCommand::usage)
                .then(BrigadierCommand.literalArgumentBuilder("server")
                        .requires(src -> src.hasPermission("dpt.send.all"))
                        .then(UtilityNodes.serverNode("originServer", dpt.getProxy(), SendCommand::usage)
                                .then(UtilityNodes.dptServerNode("destinationServer", dpt, SendCommand::usage)
                                        .then(BrigadierCommand.literalArgumentBuilder("promptPlayersToJoin")
                                                .executes(ctx -> send(ctx, dpt, ConfirmationMode.PLAYER)))
                                        .then(BrigadierCommand.literalArgumentBuilder("promptMeToSend")
                                                .requires(src -> src instanceof Player) // needs interactive.
                                                .executes(ctx -> send(ctx, dpt, ConfirmationMode.SENDER)))
                                        .executes(ctx -> send(ctx, dpt, ConfirmationMode.NONE))
                                )
                        )
                )
                .then(UtilityNodes.playerNode("player", dpt.getProxy(), SendCommand::usage)
                        .then(UtilityNodes.dptServerNode("destinationServer", dpt, SendCommand::usage)
                                .then(BrigadierCommand.literalArgumentBuilder("promptPlayersToJoin")
                                        .executes(ctx -> send(ctx, dpt, ConfirmationMode.PLAYER)))
                                .then(BrigadierCommand.literalArgumentBuilder("promptMeToSend")
                                        .requires(src -> src instanceof Player) // needs interactive.
                                        .executes(ctx -> send(ctx, dpt, ConfirmationMode.SENDER)))
                                .executes(ctx -> send(ctx, dpt, ConfirmationMode.NONE))
                ))
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
                .executes(SendCommand::usage)
                .then(UtilityNodes.serverNode("destinationServer", dpt.getProxy(), SendCommand::usage)
                        .then(BrigadierCommand.literalArgumentBuilder("immediate")
                                .executes(ctx -> send(ctx, dpt, ConfirmationMode.NONE)))
                        .executes(ctx -> send(ctx, dpt, ConfirmationMode.SENDER))
                ).build();

        return new BrigadierCommand(node);
    }

    private static int send(CommandContext<CommandSource> ctx, Dpt dpt, ConfirmationMode confirmationMode) {
        Collection<Player> players;
        if (ctx.getArguments().containsKey("originServer")) {
            var optOrigin = dpt.getProxy().getServer(ctx.getArgument("originServer", String.class))
                    .map(RegisteredServer::getPlayersConnected);
            if (optOrigin.isEmpty()) { return 1; } // TODO: ERROR
            players = optOrigin.get();
        } else if (ctx.getArguments().containsKey("player")) {
            players = switch (ctx.getArgument("player", String.class).toLowerCase()) {
                case "all" -> ctx.getSource().hasPermission("dpt.send.all") ? dpt.getProxy().getAllPlayers() : Collections.emptyList();
                case "current" -> ctx.getSource() instanceof Player p
                        ? p.hasPermission("dpt.send.all")
                            ? p.getCurrentServer().orElseThrow().getServer().getPlayersConnected()
                            : Collections.emptyList()
                        : Collections.emptyList();
                default -> dpt.getProxy().getPlayer(ctx.getArgument("player", String.class))
                        .map(Collections::singletonList).orElse(Collections.emptyList());
            };
            if (players.isEmpty()) {
                ctx.getSource().sendMessage(Component.text("Couldn't find player(s) specified."));
                return 1;
            }
        } else if (ctx.getArguments().containsKey("destinationServer") && ctx.getSource() instanceof Player player) {
            // this is kind of a werid way of handling this case, but in the case that the player is sending
            // themselves, there will only be a destination server argument.
            players = Collections.singletonList(player);
        } else {
            return SendCommand.usage(ctx);
        }

        if (!ctx.getSource().hasPermission("dpt.send." + ctx.getArgument("destinationServer", String.class))
                && !ctx.getSource().hasPermission("dpt.send.anywhere")){
            ctx.getSource().sendMessage(Component.text("You don't have permission to send to this server."));
            return 1;
        }
        dpt.getServer(ctx.getArgument("destinationServer", String.class)).ifPresentOrElse(server -> {
            String serverName = ctx.getArgument("destinationServer", String.class);
            dpt.getLogger().info("Starting server {}", serverName);
            server.startup().handle((_void, ex) -> {
                if (ex != null) {
                    dpt.getLogger().error("Failed to start server {}", serverName, ex);
                    ctx.getSource().sendMessage(Component.text("Failed to start server " + serverName));
                    return null;
                }
                switch (confirmationMode) {
                    case PLAYER:
                        players.forEach(player -> player.sendMessage(Component.text(" [Join] ").style(Style.style(NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                                .clickEvent(ClickEvent.callback(audience -> player.createConnectionRequest(server.getRegistered()).fireAndForget()))
                                .append(Component.text(" You have been invited to join " + serverName + "!")
                                        .style(Style.style(NamedTextColor.WHITE)))
                        ));
                        break;
                    case SENDER:
                        ctx.getSource().sendMessage(Component.text(" [Send] ").style(Style.style(NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                                .clickEvent(ClickEvent.callback(audience -> players.forEach(player -> player.createConnectionRequest(server.getRegistered()).fireAndForget())))
                                .append(Component.text(serverName + " is now ready.")
                                        .style(Style.style(NamedTextColor.WHITE)))
                        );
                        break;
                    case NONE:
                        players.forEach(player -> player.createConnectionRequest(server.getRegistered()).fireAndForget());
                        break;
                }
                return null;
            });
        }, () -> ctx.getSource().sendMessage(Component.text("Destination not registered in Dpt / doesnt exist", NamedTextColor.RED)));
        return 1;
    }

    private static int usage(CommandContext<CommandSource> ctx) {
        ctx.getSource().sendMessage(
                Component.text("Usage: /dpt <player/all/current> <destinationServer> [promptOnReady]")
                        .appendNewline()
                        .append(Component.text("/dpt server <originServer> <destinationServer> [promptOnReady]"))
        );
        return 1;
    }
}
