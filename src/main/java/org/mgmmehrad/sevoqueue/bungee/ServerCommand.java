package org.mgmmehrad.sevoqueue.bungee;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.util.ArrayList;
import java.util.List;

public class ServerCommand extends Command {
    private final QueueManager queueManager;
    private final ConfigManager configManager;

    public ServerCommand(QueueManager queueManager, ConfigManager configManager) {
        super("server");
        this.queueManager = queueManager;
        this.configManager = configManager;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer)) {
            return;
        }

        ProxiedPlayer player = (ProxiedPlayer) sender;

        if (!configManager.hasPermission(player, "server") && !queueManager.canBypassQueue(player)) {
            player.sendMessage(new TextComponent(ChatColor.RED + "You don't have permission!"));
            return;
        }

        if (args.length == 0) {
            player.sendMessage(new TextComponent(ChatColor.YELLOW + "Usage: /server <servername>"));
            return;
        }

        String serverName = args[0];

        if (queueManager.isInQueue(player)) {
            player.sendMessage(new TextComponent(ChatColor.RED + "You are already in a queue! Use /queue leave first."));
            return;
        }

        queueManager.addToQueue(player, serverName);
    }

    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (!configManager.hasPermission(sender, "tabcomplete")) {
            return suggestions;
        }

        if (args.length == 1) {
            for (ServerInfo server : ProxyServer.getInstance().getServers().values()) {
                String serverName = server.getName();
                if (serverName.toLowerCase().startsWith(args[0].toLowerCase())) {
                    suggestions.add(serverName);
                }
            }
        }

        return suggestions;
    }
}