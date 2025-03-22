package com.github.cinnamondev.dpt.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;


public class UtilityNodes {
    public static RequiredArgumentBuilder<CommandSource, String> playerNode(String argumentName, ProxyServer proxy, Command<CommandSource> fallback) {
        return BrigadierCommand
                .requiredArgumentBuilder(argumentName, StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    String arg = ctx.getArguments().containsKey(argumentName)
                            ? ctx.getArgument(argumentName, String.class) : "";

                    proxy.getAllPlayers().stream()
                            .map(Player::getUsername)
                            .filter(name -> name.regionMatches(true, 0, arg, 0, arg.length()))
                            .forEach(builder::suggest);

                    if ("all".regionMatches(true, 0, arg, 0, arg.length())) {
                        builder.suggest("all");
                    }
                    if ("current".regionMatches(true, 0, arg, 0, arg.length())
                            && ctx.getSource() instanceof Player) {
                        builder.suggest("current");
                    }
                    return builder.buildFuture();
                })
                .executes(fallback);
    }
    public static RequiredArgumentBuilder<CommandSource, String> serverNode(String argumentName, ProxyServer proxy, Command<CommandSource> fallback) {
        return BrigadierCommand
                .requiredArgumentBuilder(argumentName, StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    String arg = ctx.getArguments().containsKey(argumentName)
                            ? ctx.getArgument(argumentName, String.class) : "";

                    proxy.getAllServers().stream()
                            .map(server -> server.getServerInfo().getName())
                            .filter(name -> name.regionMatches(true, 0, arg, 0, arg.length()))
                            .forEach(builder::suggest);

                    return builder.buildFuture();
                })
                .executes(fallback);
    }
}
