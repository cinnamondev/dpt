package com.github.cinnamondev.dptCompanion.commands;

import com.github.cinnamondev.dptCompanion.DptCompanion;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class WDCommand implements CommandExecutor {
    private DptCompanion plugin;
    public WDCommand(DptCompanion plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        if (strings.length != 1) {
            commandSender.sendMessage("/dptwd <start/stop>");
            return false;
        }
        String controlWord = strings[0];

        switch (controlWord) {
            case "start":
                commandSender.sendMessage("telling proxy to stop ignoring me...");
                if (plugin.watchDogDoNotIgnoreMe() == false) {
                    commandSender.sendMessage("could not send message, no players online :(");
                }
                break;
            case "stop":
                commandSender.sendMessage("telling proxy to start ignoring me...");
                 if (plugin.watchDogIgnoreMe() == false) {
                     commandSender.sendMessage("could not send message, no players online :(");
                 }
                break;
            default:
                commandSender.sendMessage("/dptwd <start/stop>");
                return false;
        }


        return true;
    }
}
