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

public class QueueCommand extends Command {
    private final QueueManager queueManager;
    private final ConfigManager configManager;

    public QueueCommand(QueueManager queueManager, ConfigManager configManager) {
        super("queue", null, "q");
        this.queueManager = queueManager;
        this.configManager = configManager;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer)) {
            sender.sendMessage(new TextComponent(ChatColor.RED + "Only players can use this command!"));
            return;
        }

        ProxiedPlayer player = (ProxiedPlayer) sender;

        if (args.length == 0) {
            sendHelp(player);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "join":
                if (!configManager.hasPermission(player, "join") && !queueManager.canBypassQueue(player)) {
                    player.sendMessage(new TextComponent(ChatColor.RED + "You don't have permission!"));
                    return;
                }
                if (args.length == 2) {
                    queueManager.addToQueue(player, args[1]);
                } else {
                    player.sendMessage(new TextComponent(ChatColor.YELLOW + "Usage: /queue join <server>"));
                }
                break;

            case "leave":
                if (!configManager.hasPermission(player, "leave")) {
                    player.sendMessage(new TextComponent(ChatColor.RED + "You don't have permission!"));
                    return;
                }
                queueManager.removeFromQueue(player);
                break;

            case "status":
                if (!configManager.hasPermission(player, "status")) {
                    player.sendMessage(new TextComponent(ChatColor.RED + "You don't have permission!"));
                    return;
                }
                if (queueManager.isInQueue(player)) {
                    String server = queueManager.getPlayerQueue(player);
                    player.sendMessage(new TextComponent(ChatColor.GREEN + "You are in queue for: " + ChatColor.YELLOW + server));
                } else {
                    player.sendMessage(new TextComponent(ChatColor.RED + "You are not in any queue!"));
                }
                break;

            default:
                sendHelp(player);
        }
    }

    private void sendHelp(ProxiedPlayer player) {
        player.sendMessage(new TextComponent(ChatColor.GOLD + "===== SevoQueue Help ====="));
        player.sendMessage(new TextComponent(ChatColor.YELLOW + "/queue join <server> " + ChatColor.GRAY + "- Join queue for a server"));
        player.sendMessage(new TextComponent(ChatColor.YELLOW + "/queue leave " + ChatColor.GRAY + "- Leave current queue"));
        player.sendMessage(new TextComponent(ChatColor.YELLOW + "/queue status " + ChatColor.GRAY + "- Check your queue status"));
    }

    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (!configManager.hasPermission(sender, "tabcomplete")) {
            return suggestions;
        }

        if (args.length == 1) {
            String[] options = {"join", "leave", "status"};
            for (String option : options) {
                if (option.startsWith(args[0].toLowerCase())) {
                    suggestions.add(option);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            for (ServerInfo server : ProxyServer.getInstance().getServers().values()) {
                String serverName = server.getName();
                if (serverName.toLowerCase().startsWith(args[1].toLowerCase())) {
                    suggestions.add(serverName);
                }
            }
        }

        return suggestions;
    }
}