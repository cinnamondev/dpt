package com.github.cinnamondev.dpt.commands;

import com.github.cinnamondev.dpt.Dpt;
import com.github.cinnamondev.dpt.VelocityPTServer;
import com.github.cinnamondev.dpt.client.PowerAction;
import com.github.cinnamondev.dpt.util.ConfirmMode;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class SendCommand {
    public static BrigadierCommand sendCommand(Dpt dpt) {
        LiteralCommandNode<CommandSource> node = BrigadierCommand.literalArgumentBuilder("dptsend")
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
                                .executes(ctx -> commandParser(dpt, ctx, 0))
                                .then(BrigadierCommand.requiredArgumentBuilder("delayMS", IntegerArgumentType.integer())
                                        .executes(ctx -> commandParser(dpt, ctx, IntegerArgumentType.getInteger(ctx, "time")))
                                )
                        )
                )
                .build();

        return new BrigadierCommand(node);
    }

    private static Collection<Player> collectPlayers(CommandContext<CommandSource> ctx, ProxyServer proxy, String playerArg) {
        if (playerArg.equals("all")) {
            return proxy.getAllPlayers();
        } else if (playerArg.equals("here")) {
            if (ctx.getSource() instanceof Player p) {
                return p.getCurrentServer().orElseThrow().getServer().getPlayersConnected();
            } else {
                ctx.getSource().sendMessage(Component.text("`here` can only be used by players"));
            }
        } else {
            Optional<Player> player = proxy.getPlayer(playerArg);
            if (player.isPresent()) {
                return Collections.singleton(player.get());
            } else {
                ctx.getSource().sendMessage(Component.text("`" + playerArg + "` not found"));
            }
        }
        return Collections.emptyList();
    }

    private static int commandParser(Dpt dpt, CommandContext<CommandSource> ctx, long delayTime) {
        String playerArg = StringArgumentType.getString(ctx, "player");
        String serverArg = StringArgumentType.getString(ctx, "server");

        if (!ctx.getSource().hasPermission("dpt.send." + serverArg)) {
            ctx.getSource().sendMessage(Component.text("You do not have sufficient permission to send players here."));
            return 1;

        }
        Collection<Player> playersToSend = collectPlayers(ctx, dpt.getProxy(), playerArg);
        // this should be explored as the specific call case is confirmed.
        boolean waitForConfirm = dpt.getConfirmMode().equals(ConfirmMode.ALWAYS);

        if (ctx.getSource() instanceof Player p && playerArg.equals(p.getUsername())) {
            // player is sending themselves (requires no `other`) permission
            waitForConfirm = dpt.getConfirmMode().equals(ConfirmMode.PERSONAL);
            playersToSend = Collections.singleton(p);
        } else if (ctx.getSource().hasPermission("dpt.send.others")) {
            playersToSend = collectPlayers(ctx, dpt.getProxy(), playerArg);
        }

        if (playersToSend.isEmpty()) { return 1; }

        Optional<VelocityPTServer> server = dpt.getServer(serverArg);

        if (server.isPresent()) {
            if (!server.get().online()) {server.get().power(PowerAction.START);}

            Scheduler.TaskBuilder task = dpt.sendPlayerTask(
                    ctx.getSource(),
                    server.get(),
                    serverArg,
                    playersToSend,
                    waitForConfirm
            );

            if (delayTime > 0) {
                task = task.delay(delayTime, TimeUnit.MILLISECONDS);
            }
            task.schedule();
        } else {
            ctx.getSource().sendMessage(Component.text("Server" + serverArg + " not found"));
        }

        return 1;
    }



}
