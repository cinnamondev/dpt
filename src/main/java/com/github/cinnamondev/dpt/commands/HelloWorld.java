package com.github.cinnamondev.dpt.commands;

import com.github.cinnamondev.dpt.Dpt;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;

public final class HelloWorld {
    public static BrigadierCommand createBrigadierCommand(Dpt dpt) {
        LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("dpthello")
                .requires(src -> src.hasPermission("dpt.hello"))
                .executes(ctx -> {
                    if (ctx.getSource() instanceof Player p) {
                        p.sendMessage(Component.text("hello player"));
                        p.sendMessage(Component.text("somethingsomething " + dpt.getConfirmMode().toString()));
                    } else {
                        ctx.getSource().sendMessage(Component.text("hello world"));
                    }
                    return 1;
                })
                .then(BrigadierCommand.requiredArgumentBuilder("foobar", StringArgumentType.word())
                        .suggests((ctx,builder) -> {
                            builder.suggest("biz");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            if (ctx.getSource() instanceof Player p) {
                                p.sendMessage(Component.text("hello player " + p.getUsername() + " " + ctx.getArgument("foobar", String.class)));
                            }
                            return 1;
                        })
                )
                .build();
        return new BrigadierCommand(node);
    }
}
