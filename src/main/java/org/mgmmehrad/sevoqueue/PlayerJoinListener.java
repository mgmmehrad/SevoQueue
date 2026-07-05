package org.mgmmehrad.sevoqueue;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import java.util.Optional;

public class PlayerJoinListener {
    private final ProxyServer server;
    private final Logger logger;
    private final QueueManager queueManager;
    private final ConfigManager configManager;

    public PlayerJoinListener(ProxyServer server, Logger logger, QueueManager queueManager, ConfigManager configManager) {
        this.server = server;
        this.logger = logger;
        this.queueManager = queueManager;
        this.configManager = configManager;
    }

    @Subscribe
    public void onPlayerChooseInitialServer(PlayerChooseInitialServerEvent event) {
        Player player = event.getPlayer();
        String defaultServer = configManager.getDefaultServer();

        server.getServer(defaultServer).ifPresentOrElse(
                s -> {
                    event.setInitialServer(s);
                    sendMessage(player, "&aWelcome! You have been connected to &e" + defaultServer);
                    // logger.info("Sent {} to default server: {}", player.getUsername(), defaultServer);
                },
                () -> {
                    logger.warn("Default server {} not found!", defaultServer);
                    event.setInitialServer(null);
                }
        );
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        String targetServer = event.getOriginalServer().getServerInfo().getName();

        if (queueManager.isConnectingViaQueue(player)) {
            // logger.info("Allowing queue-initiated connection for {} to {}", player.getUsername(), targetServer);
            queueManager.setConnectingViaQueue(player, false);
            return;
        }

        if (queueManager.isInQueue(player)) {
            // logger.info("Player {} is in queue, blocking connection to {}", player.getUsername(), targetServer);
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            sendMessage(player, "&cYou are in queue! Use &e/queue leave &cto cancel.");
            return;
        }

        if (!queueManager.getAddons().canConnectToServer(targetServer)) {
            String statusMsg = queueManager.getAddons().getServerStatusMessage(targetServer);
            if (statusMsg != null) {
                // logger.info("Server {} is not available for {}", targetServer, player.getUsername());
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                sendMessage(player, statusMsg);
                return;
            }
        }

        if (queueManager.canBypassQueue(player)) {
            queueManager.removeFromQueue(player, false);
            // logger.info("Allowing bypass connection for {} to {}", player.getUsername(), targetServer);
            return;
        }

        if (player.getCurrentServer().isEmpty()) {
            // logger.info("Allowing initial connection for {} to {}", player.getUsername(), targetServer);
            return;
        }

        if (configManager.isVelocityServerCommand()) {
            // logger.info("Blocking direct /server command for {} to {}", player.getUsername(), targetServer);
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            queueManager.addToQueue(player, targetServer);
        } else {
            // logger.info("Allowing direct connection for {} to {}", player.getUsername(), targetServer);
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        queueManager.removeFromQueue(player, false);
        queueManager.setConnectingViaQueue(player, false);
    }

    private void sendMessage(Player player, String message) {
        player.sendMessage(formatMessage(message));
    }

    private Component formatMessage(String message) {
        String normalizedMessage = message == null ? "" : message.replace((char) 167, '&');
        return LegacyComponentSerializer.legacyAmpersand().deserialize(normalizedMessage);
    }
}