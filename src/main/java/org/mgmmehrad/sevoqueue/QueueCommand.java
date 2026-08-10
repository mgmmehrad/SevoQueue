package org.mgmmehrad.sevoqueue;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class QueueCommand implements SimpleCommand {
    private final QueueManager queueManager;
    private final ConfigManager configManager;

    public QueueCommand(QueueManager queueManager, ConfigManager configManager) {
        this.queueManager = queueManager;
        this.configManager = configManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!(source instanceof Player)) {
            source.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cOnly players can use this command!"));
            return;
        }

        Player player = (Player) source;

        if (args.length == 0) {
            sendHelp(player);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "join":
                if (args.length == 2) {
                    queueManager.addToQueue(player, args[1]);
                } else {
                    player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cUsage: /queue join <server>"));
                }
                break;

            case "leave":
                if (!configManager.hasPermission(player, "leave")) {
                    player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cYou don't have permission!"));
                    return;
                }
                Set<String> queues = queueManager.getPlayerQueues(player);
                if (queues.isEmpty()) {
                    player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(configManager.getNotInQueueMsg()));
                    return;
                }

                if (args.length == 1) { // فقط زده /queue leave
                    if (queues.size() > 1) {
                        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(configManager.getMultipleQueuesLeaveMsg()));
                        return;
                    }
                    String server = queues.iterator().next();
                    queueManager.removeFromQueue(player, server, true);
                } else {
                    String server = args[1];
                    queueManager.removeFromQueue(player, server, true);
                }
                break;

            case "status":
                if (!configManager.hasPermission(player, "status")) {
                    player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cYou don't have permission!"));
                    return;
                }
                Set<String> currentQueues = queueManager.getPlayerQueues(player);
                if (!currentQueues.isEmpty()) {
                    String serversStr = String.join(", ", currentQueues);
                    player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(
                            configManager.getStatusInQueuesMsg().replace("{servers}", serversStr)));
                } else {
                    player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(configManager.getNotInQueueMsg()));
                }
                break;

            default:
                sendHelp(player);
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&6===== Queue Help ====="));
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&e/queue join <server> &7- Join queue for a server"));
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&e/queue leave [server] &7- Leave current queue"));
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&e/queue status &7- Check your queue status"));
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();
        List<String> suggestions = new ArrayList<>();

        if (!configManager.hasPermission(invocation.source(), "tabcomplete")) {
            return CompletableFuture.completedFuture(suggestions);
        }

        if (args.length == 1) {
            String[] options = {"join", "leave", "status"};
            for (String option : options) {
                if (option.startsWith(args[0].toLowerCase())) {
                    suggestions.add(option);
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("join")) {
                for (RegisteredServer server : queueManager.getAllServers()) {
                    String serverName = server.getServerInfo().getName();
                    if (serverName.toLowerCase().startsWith(args[1].toLowerCase())) {
                        suggestions.add(serverName);
                    }
                }
            } else if (args[0].equalsIgnoreCase("leave") && invocation.source() instanceof Player) {
                Player player = (Player) invocation.source();
                for (String q : queueManager.getPlayerQueues(player)) {
                    if (q.toLowerCase().startsWith(args[1].toLowerCase())) {
                        suggestions.add(q);
                    }
                }
            }
        }

        return CompletableFuture.completedFuture(suggestions);
    }
}