package org.mgmmehrad.sevoqueue.bungee;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public class QueueManager {

    private final Plugin plugin;
    private final ProxyServer proxy;
    private final ConfigManager configManager;
    private final Addons addons;

    private final Map<String, Queue<UUID>> serverQueues = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerQueueMap = new ConcurrentHashMap<>();
    private final Map<String, Long> nextQueueReadyTime = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> connectingViaQueue = new ConcurrentHashMap<>();

    public QueueManager(Plugin plugin, ConfigManager configManager, Addons addons) {
        this.plugin = plugin;
        this.proxy = plugin.getProxy();
        this.configManager = configManager;
        this.addons = addons;

        startQueueProcessor();
        startActionBarUpdater();
    }

    public void connectToServer(ProxiedPlayer player, ServerInfo targetServer) {

        connectingViaQueue.put(player.getUniqueId(), true);

        player.connect(targetServer, (result, error) -> {

            connectingViaQueue.remove(player.getUniqueId());

            if (error != null) {

                plugin.getLogger().warning(
                        "Failed to connect "
                                + player.getName()
                                + " to "
                                + targetServer.getName()
                );

                sendMessage(
                        player,
                        ChatColor.RED + "Failed to connect to "
                                + targetServer.getName()
                );
            }
        });
    }

    public void addToQueue(ProxiedPlayer player, String serverName) {

        UUID playerId = player.getUniqueId();

        ServerInfo targetServer = proxy.getServerInfo(serverName);

        if (targetServer == null) {

            sendMessage(
                    player,
                    ChatColor.RED + "Server "
                            + serverName
                            + " does not exist!"
            );

            return;
        }

        String targetServerName = targetServer.getName();

        if (player.getServer() != null
                && player.getServer().getInfo().getName()
                .equalsIgnoreCase(targetServerName)) {

            sendMessage(
                    player,
                    addons.getAlreadyConnectedMessage(targetServerName)
            );

            return;
        }

        if (!addons.canConnectToServer(serverName)) {

            String statusMsg =
                    addons.getServerStatusMessage(serverName);

            if (statusMsg != null) {
                sendMessage(player, statusMsg);
            } else {

                sendMessage(
                        player,
                        ChatColor.RED
                                + "Error: Server is not available right now."
                );
            }

            return;
        }

        if (canBypassQueue(player)) {

            if (playerQueueMap.containsKey(playerId)) {
                removeFromQueue(player, false);
            }

            sendMessage(
                    player,
                    ChatColor.GREEN
                            + "Bypassing queue, connecting to "
                            + serverName
                            + "..."
            );

            connectToServer(player, targetServer);

            plugin.getLogger().info(
                    player.getName()
                            + " bypassed the queue for "
                            + serverName
            );

            return;
        }

        if (playerQueueMap.containsKey(playerId)) {

            sendMessage(
                    player,
                    ChatColor.RED
                            + "You are already in a queue! Use /queue leave first."
            );

            return;
        }

        serverQueues.computeIfAbsent(
                serverName,
                k -> new ConcurrentLinkedQueue<>()
        );

        Queue<UUID> queue = serverQueues.get(serverName);

        boolean wasEmpty = queue.isEmpty();

        if (queue.contains(playerId)) {

            sendMessage(
                    player,
                    ChatColor.RED
                            + "You are already in the queue for "
                            + serverName
            );

            return;
        }

        queue.add(playerId);
        playerQueueMap.put(playerId, serverName);

        if (wasEmpty) {

            nextQueueReadyTime.put(
                    serverName,
                    System.currentTimeMillis()
                            + getQueueDelayMillis()
            );
        }

        int position =
                getPositionInQueue(playerId, serverName);

        long waitTime =
                getEstimatedWaitSeconds(playerId, serverName);

        String message =
                configManager.getQueueMessage()
                        .replace("{pos}",
                                String.valueOf(position))
                        .replace("{time}",
                                String.valueOf(waitTime));

        message =
                ChatColor.translateAlternateColorCodes('&', message);

        sendMessage(player, message);

        plugin.getLogger().info(
                player.getName()
                        + " added to queue for "
                        + serverName
                        + " (Position: "
                        + position
                        + ", Wait: "
                        + waitTime
                        + "s)"
        );
    }

    public void removeFromQueue(ProxiedPlayer player) {
        removeFromQueue(player, true);
    }

    public void removeFromQueue(ProxiedPlayer player,
                                boolean notifyPlayer) {

        UUID playerId = player.getUniqueId();

        String serverName =
                playerQueueMap.remove(playerId);

        if (serverName != null) {

            Queue<UUID> queue =
                    serverQueues.get(serverName);

            if (queue != null) {

                queue.remove(playerId);

                if (queue.isEmpty()) {
                    nextQueueReadyTime.remove(serverName);
                }
            }

            if (notifyPlayer) {

                sendMessage(
                        player,
                        ChatColor.GREEN
                                + "You left the queue!"
                );
            }

            plugin.getLogger().info(
                    player.getName()
                            + " left the queue for "
                            + serverName
            );

        } else if (notifyPlayer) {

            sendMessage(
                    player,
                    ChatColor.RED
                            + "You are not in any queue!"
            );
        }
    }

    public boolean isInQueue(ProxiedPlayer player) {
        return playerQueueMap.containsKey(player.getUniqueId());
    }

    public String getPlayerQueue(ProxiedPlayer player) {
        return playerQueueMap.get(player.getUniqueId());
    }

    public boolean isConnectingViaQueue(ProxiedPlayer player) {

        return connectingViaQueue.getOrDefault(
                player.getUniqueId(),
                false
        );
    }

    public boolean canBypassQueue(ProxiedPlayer player) {

        return configManager.hasEnabledPermission(
                player,
                "bypass"
        );
    }

    public void setConnectingViaQueue(ProxiedPlayer player,
                                      boolean value) {

        if (value) {

            connectingViaQueue.put(
                    player.getUniqueId(),
                    true
            );

        } else {

            connectingViaQueue.remove(
                    player.getUniqueId()
            );
        }
    }

    public Addons getAddons() {
        return addons;
    }

    private void sendMessage(ProxiedPlayer player,
                             String message) {

        player.sendMessage(
                new TextComponent(message)
        );
    }

    // FIXED ACTIONBAR
    private void sendActionBar(ProxiedPlayer player,
                               String message) {

        BaseComponent[] components =
                new ComponentBuilder(message).create();

        player.sendMessage(
                ChatMessageType.ACTION_BAR,
                components
        );
    }

    private void startActionBarUpdater() {

        proxy.getScheduler().schedule(plugin, () -> {

            for (Map.Entry<UUID, String> entry
                    : playerQueueMap.entrySet()) {

                UUID playerId = entry.getKey();
                String serverName = entry.getValue();

                ProxiedPlayer player =
                        proxy.getPlayer(playerId);

                if (player == null) {
                    continue;
                }

                int position =
                        getPositionInQueue(
                                playerId,
                                serverName
                        );

                if (position == -1) {
                    continue;
                }

                long remainingTime =
                        getEstimatedWaitSeconds(
                                playerId,
                                serverName
                        );

                String actionBarMsg =
                        configManager.getActionBarMessage()
                                .replace(
                                        "{pos}",
                                        String.valueOf(position)
                                )
                                .replace(
                                        "{time}",
                                        String.valueOf(remainingTime)
                                )
                                .replace(
                                        "{server}",
                                        serverName
                                );

                actionBarMsg =
                        ChatColor.translateAlternateColorCodes(
                                '&',
                                actionBarMsg
                        );

                sendActionBar(player, actionBarMsg);
            }

        }, 0, 1, TimeUnit.SECONDS);
    }

    private int getPositionInQueue(UUID playerId,
                                   String serverName) {

        Queue<UUID> queue =
                serverQueues.get(serverName);

        if (queue == null) {
            return -1;
        }

        int position = 1;

        for (UUID id : queue) {

            if (id.equals(playerId)) {
                return position;
            }

            position++;
        }

        return -1;
    }

    private int getZeroBasedPositionInQueue(UUID playerId,
                                            String serverName) {

        Queue<UUID> queue =
                serverQueues.get(serverName);

        if (queue == null) {
            return -1;
        }

        int position = 0;

        for (UUID id : queue) {

            if (id.equals(playerId)) {
                return position;
            }

            position++;
        }

        return -1;
    }

    private long getEstimatedWaitSeconds(UUID playerId,
                                         String serverName) {

        int zeroBasedPosition =
                getZeroBasedPositionInQueue(
                        playerId,
                        serverName
                );

        if (zeroBasedPosition == -1) {
            return 0;
        }

        long now = System.currentTimeMillis();

        long nextReadyTime =
                nextQueueReadyTime.getOrDefault(
                        serverName,
                        now + getQueueDelayMillis()
                );

        long timeUntilHeadCanConnect =
                Math.max(
                        0L,
                        nextReadyTime - now
                );

        long estimatedMillis =
                timeUntilHeadCanConnect
                        + (zeroBasedPosition
                        * getQueueDelayMillis());

        return Math.max(
                1L,
                (estimatedMillis + 999L) / 1000L
        );
    }

    private long getQueueDelayMillis() {

        return TimeUnit.SECONDS.toMillis(
                Math.max(
                        1,
                        configManager.getQueueSec()
                )
        );
    }

    private void startQueueProcessor() {

        proxy.getScheduler().schedule(plugin, () -> {

            for (Map.Entry<String, Queue<UUID>> entry
                    : serverQueues.entrySet()) {

                String serverName = entry.getKey();

                Queue<UUID> queue = entry.getValue();

                if (queue.isEmpty()) {

                    nextQueueReadyTime.remove(serverName);

                    continue;
                }

                ServerInfo targetServer =
                        proxy.getServerInfo(serverName);

                if (targetServer == null) {
                    continue;
                }

                if (!addons.canConnectToServer(serverName)) {
                    continue;
                }

                UUID nextPlayerId = queue.peek();

                if (nextPlayerId == null) {
                    continue;
                }

                ProxiedPlayer nextPlayer =
                        proxy.getPlayer(nextPlayerId);

                if (nextPlayer == null) {

                    queue.poll();

                    playerQueueMap.remove(nextPlayerId);

                    nextQueueReadyTime.put(
                            serverName,
                            System.currentTimeMillis()
                    );

                    continue;
                }

                long now = System.currentTimeMillis();

                long readyTime =
                        nextQueueReadyTime.getOrDefault(
                                serverName,
                                now + getQueueDelayMillis()
                        );

                if (now >= readyTime) {

                    queue.poll();

                    playerQueueMap.remove(nextPlayerId);

                    if (queue.isEmpty()) {

                        nextQueueReadyTime.remove(serverName);

                    } else {

                        nextQueueReadyTime.put(
                                serverName,
                                System.currentTimeMillis()
                                        + getQueueDelayMillis()
                        );
                    }

                    sendMessage(
                            nextPlayer,
                            ChatColor.GREEN
                                    + "You are now connecting to "
                                    + serverName
                                    + "..."
                    );

                    connectToServer(nextPlayer, targetServer);

                    plugin.getLogger().info(
                            nextPlayer.getName()
                                    + " is now connecting to "
                                    + serverName
                    );
                }
            }

        }, 0, 500, TimeUnit.MILLISECONDS);
    }
}