package com.github.cinnamondev.dpt.commands;

import com.github.cinnamondev.dpt.Dpt;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

public class WatchdogCommand {
    private static void CommandHelp(CommandSource source) {
        source.sendMessage(Component.text("/dptwd <server> <start/stop>"));
    }
    public static BrigadierCommand brigadierCommand(Dpt dpt) {
        LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("dptwd")
                .requires(src -> src.hasPermission("dpt.watchdog"))
                .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                        .suggests((ctx,builder) -> {

                            return builder.buildFuture();
                        })

                .executes(ctx -> {
                    CommandHelp(ctx.getSource());
                    return 1;
                }).then(BrigadierCommand.requiredArgumentBuilder("control", StringArgumentType.word())
                                .suggests((ctx,builder) -> {
                                    builder.suggest("start");
                                    builder.suggest("stop");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String control = StringArgumentType.getString(ctx, "control");
                                    dpt.getServer(StringArgumentType.getString(ctx, "server"))
                                            .ifPresentOrElse(server -> {
                                                switch (control) {
                                                    case "start":
                                                        server.setIgnoreInactivity(false);
                                                        break;
                                                    case "stop":
                                                        server.setIgnoreInactivity(true);
                                                        break;
                                                    default:
                                                        CommandHelp(ctx.getSource());
                                                }
                                            }, () -> {
                                                ctx.getSource()
                                                        .sendMessage(
                                                                Component.text("Couldn't find server in cfg.")
                                                        );
                                            });
                                    return 1;
                                })
                        )
                ).build();
        return new BrigadierCommand(node);
    }
}
