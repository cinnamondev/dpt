package com.github.cinnamondev.dpt.commands;

import com.github.cinnamondev.dpt.Dpt;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;

public class WatchdogCommand {
    private static void CommandHelp(CommandSource source) {
        source.sendMessage(Component.text("/dptwd <server> [start/stop]"));
    }
    public static BrigadierCommand brigadierCommand(Dpt dpt) {
        LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("dptwd")
                .requires(src -> src.hasPermission("dpt.watchdog"))
                .then(UtilityNodes.serverNode("server", dpt.getProxy(), WatchdogCommand::usage)
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
        return 1;
    }
}
