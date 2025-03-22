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
                        .then(UtilityNodes.serverNode("originServer", dpt.getProxy(), SendCommand::usage)
                                .then(UtilityNodes.serverNode("destinationServer", dpt.getProxy(), SendCommand::usage)
                                        .then(BrigadierCommand.literalArgumentBuilder("promptToJoin")
                                                .executes(ctx -> send(ctx, dpt, true)))
                                        .executes(ctx -> send(ctx, dpt, false))
                                )
                        )
                )
                .then(UtilityNodes.playerNode("player", dpt.getProxy(), SendCommand::usage)
                        .then(UtilityNodes.serverNode("destinationServer", dpt.getProxy(), SendCommand::usage)
                                .then(BrigadierCommand.literalArgumentBuilder("promptToJoin")
                                        .executes(ctx -> send(ctx, dpt, true)))
                                .executes(ctx -> send(ctx, dpt, false))
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
                        .then(BrigadierCommand.literalArgumentBuilder("promptToJoin")
                                .executes(ctx -> send(ctx, dpt, true)))
                        .executes(ctx -> send(ctx, dpt, false))
                ).build();

        return new BrigadierCommand(node);
    }

    private static int send(CommandContext<CommandSource> ctx, Dpt dpt, boolean promptOnReady) {
        Collection<Player> players;
        if (ctx.getArguments().containsKey("originServer")) {
            var optOrigin = dpt.getProxy().getServer(ctx.getArgument("originServer", String.class))
                    .map(RegisteredServer::getPlayersConnected);
            if (optOrigin.isEmpty()) { return 1; } // TODO: ERROR
            players = optOrigin.get();
        } else if (ctx.getArguments().containsKey("player")) {
            var optPlayer = dpt.getProxy().getPlayer(ctx.getArgument("player", String.class));
            if (optPlayer.isEmpty()) { return 1; } // TODO: ERROR
            players = Collections.singletonList(optPlayer.get());
        } else if (ctx.getArguments().containsKey("destinationServer") && ctx.getSource() instanceof Player player) {
            // this is kind of a werid way of handling this case, but in the case that the player is sending
            // themselves, there will only be a destination server argument.
            players = Collections.singletonList(player);
        } else {
            return SendCommand.usage(ctx);
        }
        dpt.getProxy().getServer(ctx.getArgument("destinationServer", String.class)).ifPresentOrElse(
                destination -> dpt.getServer(destination.getServerInfo().getName()).ifPresentOrElse(server -> server.startup().handle((_void, ex) -> {
                    if (ex != null) { dpt.getLogger().error("sendcommand timeout", ex); return null; }

                    if (promptOnReady) {
                        players.forEach(player -> player.sendMessage(
                                Component.text("[Join]").style(Style.style(NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                                        .clickEvent(ClickEvent.callback(audience -> player.createConnectionRequest(server.getRegistered()).fireAndForget()))
                                        .append(Component.text(" You have been invited to join " + server.getRegistered().getServerInfo().getName() + "!"))
                        ));
                    } else {
                        players.forEach(player -> player.createConnectionRequest(server.getRegistered()).fireAndForget());
                    }
                    return null;
                }), () -> ctx.getSource().sendMessage(Component.text("Destination found, but not registered in Dpt", NamedTextColor.RED))),
                () -> ctx.getSource().sendMessage(Component.text("Destination not found", NamedTextColor.RED)) // server not found
        );
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
