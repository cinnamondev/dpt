package com.github.cinnamondev.dpt.commands;

import com.github.cinnamondev.dpt.Dpt;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;

public class WatchdogCommand {
    public static BrigadierCommand command(Dpt dpt) {
        LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("dptwd")
                .requires(src -> src.hasPermission("dpt.watchdog"))
                .then(UtilityNodes.dptServerNode("server", dpt, WatchdogCommand::usage)
                        .then(BrigadierCommand.literalArgumentBuilder("start").executes(ctx -> {
                            dpt.getServer(ctx.getArgument("server", String.class)).ifPresentOrElse(server -> {
                                server.watchdogEnabled(true);
                            }, () -> ctx.getSource().sendMessage(Component.text("Server not found")));
                            return 1;
                        }))
                        .then(BrigadierCommand.literalArgumentBuilder("stop").executes(ctx -> {
                            dpt.getServer(ctx.getArgument("server", String.class)).ifPresentOrElse(server -> {
                                server.watchdogEnabled(false);
                            }, () -> ctx.getSource().sendMessage(Component.text("Server not found")));
                            return 1;
                        }))
                        .executes(ctx -> {
                            dpt.getServer(ctx.getArgument("server", String.class)).ifPresentOrElse(server -> {
                                ctx.getSource().sendMessage(Component.text(
                                        "Watchdog is " + (server.watchdogEnabled() ? "enabled" : "disabled")
                                                + " for server " + server.getRegistered().getServerInfo().getName()
                                ));
                            }, () -> ctx.getSource().sendMessage(Component.text("Server not found")));
                            return 1;
                        })
                )
                .executes(WatchdogCommand::usage)
                .build();
        return new BrigadierCommand(node);
    }

    private static int usage(CommandContext<CommandSource> ctx) {
        ctx.getSource().sendMessage(Component.text("/dptwd <server> [start/stop]"));
        return 1;
    }
}
