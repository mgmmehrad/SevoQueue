package org.mgmmehrad.sevoqueue.bungee;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

public class PlayerJoinListener implements Listener {
    private final Plugin plugin;
    private final QueueManager queueManager;
    private final ConfigManager configManager;

    public PlayerJoinListener(Plugin plugin, QueueManager queueManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.queueManager = queueManager;
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onServerConnect(ServerConnectEvent event) {
        ProxiedPlayer player = event.getPlayer();

        if (event.getTarget() == null) {
            String defaultServer = configManager.getDefaultServer();
            ServerInfo defaultServerInfo = plugin.getProxy().getServerInfo(defaultServer);

            if (defaultServerInfo != null) {
                event.setTarget(defaultServerInfo);
                player.sendMessage(new TextComponent(ChatColor.GREEN + "Welcome! You have been connected to " + ChatColor.YELLOW + defaultServer));
            }
            return;
        }

        String targetServer = event.getTarget().getName();

        if (queueManager.isConnectingViaQueue(player)) {
            plugin.getLogger().info("Allowing queue-initiated connection for " + player.getName() + " to " + targetServer);
            queueManager.setConnectingViaQueue(player, false);
            return;
        }

        if (queueManager.isInQueue(player)) {
            plugin.getLogger().info("Player " + player.getName() + " is in queue, blocking connection to " + targetServer);
            event.setCancelled(true);
            player.sendMessage(new TextComponent(ChatColor.RED + "You are in queue! Use /queue leave to cancel."));
            return;
        }

        if (!queueManager.getAddons().canConnectToServer(targetServer)) {
            String statusMsg = queueManager.getAddons().getServerStatusMessage(targetServer);
            if (statusMsg != null) {
                plugin.getLogger().info("Server " + targetServer + " is not available for " + player.getName());
                event.setCancelled(true);
                player.sendMessage(new TextComponent(statusMsg));
                return;
            }
        }

        if (queueManager.canBypassQueue(player)) {
            queueManager.removeFromQueue(player, false);
            plugin.getLogger().info("Allowing bypass connection for " + player.getName() + " to " + targetServer);
            return;
        }

        if (configManager.isVelocityServerCommand() && player.getServer() != null) {
            plugin.getLogger().info("Blocking direct connection for " + player.getName() + " to " + targetServer + " - adding to queue");
            event.setCancelled(true);
            queueManager.addToQueue(player, targetServer);
        }
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        ProxiedPlayer player = event.getPlayer();
        queueManager.removeFromQueue(player, false);
        queueManager.setConnectingViaQueue(player, false);
    }

    @EventHandler
    public void onServerConnected(ServerConnectedEvent event) {
        ProxiedPlayer player = event.getPlayer();
        if (queueManager.isInQueue(player)) {
            queueManager.removeFromQueue(player, false);
        }
    }
}