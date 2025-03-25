package com.github.cinnamondev.dpt.commands;

import com.github.cinnamondev.dpt.Dpt;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.concurrent.CompletableFuture;

public class PingCommand {
    public static BrigadierCommand command(Dpt dpt) {
        LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("dptping").then(
                UtilityNodes.serverNode("server", dpt.getProxy(), PingCommand::usage)
                        .executes(ctx -> {
                            dpt.getServer(ctx.getArgument("server", String.class)).ifPresentOrElse(server -> {
                                var pingFuture = server.getPingAsComponent();
                                var resourceFuture = server.getResourcesAsComponent();
                                CompletableFuture.allOf(pingFuture, resourceFuture).whenComplete((_void, ex) -> {
                                    if (ex != null) {
                                        ctx.getSource().sendMessage(Component.text("Failed to ping :(", NamedTextColor.RED));
                                        dpt.getLogger().warn("Failed to ping :(", ex);
                                    } else {
                                        ctx.getSource().sendMessage(
                                                Component.text("Pong! ")
                                                        .append(pingFuture.getNow(Component.empty()))
                                                        .appendSpace()
                                                        .append(resourceFuture.getNow(Component.empty()))
                                        );
                                    }
                                });
                            }, () -> ctx.getSource().sendMessage(Component.text("Server not found", NamedTextColor.RED)));
                            return 1;
                        })
        ).build();
        return new BrigadierCommand(node);
    }

    public static int usage(CommandContext<CommandSource> ctx) {
        ctx.getSource().sendMessage(Component.text("/dptping <server>"));
        return 1;
    }
}
