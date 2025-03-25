package com.github.cinnamondev.dpt.commands;

import com.github.cinnamondev.dpt.Dpt;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;


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
    public static RequiredArgumentBuilder<CommandSource, String> dptServerNode(String argumentName, Dpt dpt, Command<CommandSource> fallback) {
        return BrigadierCommand
                .requiredArgumentBuilder(argumentName, StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    String arg = ctx.getArguments().containsKey(argumentName)
                            ? ctx.getArgument(argumentName, String.class) : "";

                    dpt.getDptServers().keySet().stream()
                            .filter(name -> name.regionMatches(true, 0, arg, 0, arg.length()))
                            .forEach(builder::suggest);

                    return builder.buildFuture();
                })
                .executes(fallback);
    }

    public static String bytesToReadableBytes(long bytes) {
        // from: https://stackoverflow.com/questions/3758606/how-can-i-convert-byte-size-into-a-human-readable-format-in-java
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024) { return bytes + " B"; }
        long value = absB;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        return String.format("%.1f %ciB", value / 1024.0, ci.current());
    }
}
