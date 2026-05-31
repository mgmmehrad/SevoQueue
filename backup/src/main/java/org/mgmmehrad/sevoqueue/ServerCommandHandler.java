package org.mgmmehrad.sevoqueue;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

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

        if (!configManager.hasPermission(player, "server") && !queueManager.canBypassQueue(player)) {
            player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cYou don't have permission!"));
            return;
        }

        if (args.length == 0) {
            player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cUsage: /server <servername>"));
            return;
        }

        String serverName = args[0];

        if (queueManager.isInQueue(player)) {
            player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cYou are already in a queue! Use &e/queue leave &cfirst."));
            return;
        }

        queueManager.addToQueue(player, serverName);
    }
}
