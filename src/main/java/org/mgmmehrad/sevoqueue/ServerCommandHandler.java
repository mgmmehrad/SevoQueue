package org.mgmmehrad.sevoqueue;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ServerCommandHandler implements SimpleCommand {
    private final ProxyServer server;
    private final ConfigManager configManager;
    private final QueueManager queueManager;

    public ServerCommandHandler(ProxyServer server, Logger logger, ConfigManager configManager, QueueManager queueManager) {
        this.server = server;
        this.configManager = configManager;
        this.queueManager = queueManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!(source instanceof Player)) {
            return;
        }

        Player player = (Player) source;

        if (args.length == 0) {
            player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cUsage: /server <servername>"));
            return;
        }

        String serverName = args[0];
        queueManager.addToQueue(player, serverName);
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();
        List<String> suggestions = new ArrayList<>();

        if (!configManager.hasPermission(invocation.source(), "tabcomplete")) {
            return CompletableFuture.completedFuture(suggestions);
        }

        if (args.length == 1) {
            for (RegisteredServer s : server.getAllServers()) {
                String serverName = s.getServerInfo().getName();
                if (serverName.toLowerCase().startsWith(args[0].toLowerCase())) {
                    suggestions.add(serverName);
                }
            }
        }

        return CompletableFuture.completedFuture(suggestions);
    }
}