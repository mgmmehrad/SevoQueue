package org.mgmmehrad.sevoqueue;

import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public class QueueManager {
    private final ProxyServer server;
    private final Logger logger;
    private final ConfigManager configManager;
    private final Object pluginInstance;
    private Addons addons;

    private final Map<String, Queue<UUID>> serverQueues = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerQueues = new ConcurrentHashMap<>();
    private final Map<String, Long> nextQueueReadyTime = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> connectingViaQueue = new ConcurrentHashMap<>();

    public QueueManager(ProxyServer server, Logger logger, ConfigManager configManager, Object pluginInstance) {
        this.server = server;
        this.logger = logger;
        this.configManager = configManager;
        this.pluginInstance = pluginInstance;
        this.addons = new Addons(server, logger, pluginInstance);
        startQueueProcessor();
        startActionBarUpdater();
    }

    public void addToQueue(Player player, String serverName) {
        UUID playerId = player.getUniqueId();

        Optional<RegisteredServer> targetServer = server.getServer(serverName);
        if (targetServer.isEmpty()) {
            sendMessage(player, "&cServer " + serverName + " does not exist!");
            return;
        }
        String targetServerName = targetServer.get().getServerInfo().getName();

        if (player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName().equalsIgnoreCase(targetServerName))
                .orElse(false)) {
            sendMessage(player, addons.getAlreadyConnectedMessage(targetServerName));
            return;
        }
        serverName = targetServerName;

        Addons.ServerStatus status = addons.getCurrentStatusPublic(serverName);
        boolean serverConnectable = status == Addons.ServerStatus.ONLINE;

        if (canBypassQueue(player) && serverConnectable) {
            removeFromAllQueues(player, false);
            sendMessage(player, "&aBypassing queue, connecting to " + serverName + "...");
            connectingViaQueue.put(playerId, true);
            player.createConnectionRequest(targetServer.get()).connect()
                    .whenComplete((result, throwable) -> {
                        connectingViaQueue.remove(playerId);
                        handleConnectionResult(player, result, throwable);
                    });
            return;
        }

        Set<String> queues = playerQueues.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());

        if (queues.contains(serverName)) {
            sendMessage(player, configManager.getAlreadyInQueueMsg().replace("{server}", serverName));
            return;
        }

        serverQueues.computeIfAbsent(serverName, k -> new ConcurrentLinkedQueue<>());
        Queue<UUID> queue = serverQueues.get(serverName);
        boolean wasEmpty = queue.isEmpty();

        queue.add(playerId);
        queues.add(serverName);

        if (!serverConnectable) {
            nextQueueReadyTime.remove(serverName);
        } else if (wasEmpty) {
            nextQueueReadyTime.put(serverName, System.currentTimeMillis() + getQueueDelayMillis());
        }

        int position = queue.size();

        if (serverConnectable) {
            long waitTime = getEstimatedWaitSeconds(playerId, serverName);
            String message = configManager.getQueueMessage()
                    .replace("{pos}", String.valueOf(position))
                    .replace("{time}", String.valueOf(waitTime));
            sendMessage(player, message);
        } else {
            String reason = getReasonString(status);
            String message = configManager.getServerCantConnectMessage()
                    .replace("{pos}", String.valueOf(position))
                    .replace("{Reason}", reason);
            sendMessage(player, message);
        }
    }

    public void removeFromQueue(Player player, String serverName, boolean notifyPlayer) {
        UUID playerId = player.getUniqueId();
        Set<String> queues = playerQueues.get(playerId);

        if (queues != null && queues.contains(serverName)) {
            queues.remove(serverName);
            if (queues.isEmpty()) {
                playerQueues.remove(playerId);
            }

            Queue<UUID> queue = serverQueues.get(serverName);
            if (queue != null) {
                queue.remove(playerId);
                if (queue.isEmpty()) {
                    nextQueueReadyTime.remove(serverName);
                }
            }
            if (notifyPlayer) {
                sendMessage(player, configManager.getLeftQueueMsg().replace("{server}", serverName));
            }
            logger.info("{} left the queue for {}", player.getUsername(), serverName);
        } else if (notifyPlayer) {
            sendMessage(player, configManager.getNotInSpecificQueueMsg().replace("{server}", serverName));
        }
    }

    public void removeFromAllQueues(Player player, boolean notifyPlayer) {
        UUID playerId = player.getUniqueId();
        Set<String> queues = playerQueues.remove(playerId);

        if (queues != null && !queues.isEmpty()) {
            for (String serverName : queues) {
                Queue<UUID> queue = serverQueues.get(serverName);
                if (queue != null) {
                    queue.remove(playerId);
                    if (queue.isEmpty()) {
                        nextQueueReadyTime.remove(serverName);
                    }
                }
                if (notifyPlayer) {
                    sendMessage(player, configManager.getLeftQueueMsg().replace("{server}", serverName));
                }
                logger.info("{} left the queue for {}", player.getUsername(), serverName);
            }
        } else if (notifyPlayer) {
            sendMessage(player, configManager.getNotInQueueMsg());
        }
    }

    public boolean isInQueue(Player player) {
        Set<String> queues = playerQueues.get(player.getUniqueId());
        return queues != null && !queues.isEmpty();
    }

    public Set<String> getPlayerQueues(Player player) {
        return playerQueues.getOrDefault(player.getUniqueId(), Collections.emptySet());
    }

    public boolean isConnectingViaQueue(Player player) {
        return connectingViaQueue.getOrDefault(player.getUniqueId(), false);
    }

    public boolean canBypassQueue(Player player) {
        return configManager.hasEnabledPermission(player, "bypass");
    }

    public void setConnectingViaQueue(Player player, boolean value) {
        if (value) {
            connectingViaQueue.put(player.getUniqueId(), true);
        } else {
            connectingViaQueue.remove(player.getUniqueId());
        }
    }

    public Collection<RegisteredServer> getAllServers() {
        return server.getAllServers();
    }

    public Addons getAddons() {
        return addons;
    }

    private void sendMessage(Player player, String message) {
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(normalizeLegacyColors(message)));
    }

    private void sendActionBar(Player player, String message) {
        player.sendActionBar(LegacyComponentSerializer.legacyAmpersand().deserialize(normalizeLegacyColors(message)));
    }

    private String normalizeLegacyColors(String message) {
        return message == null ? "" : message.replace((char) 167, '&');
    }

    private String getReasonString(Addons.ServerStatus status) {
        switch (status) {
            case FULL: return "Full";
            case OFFLINE: return "Offline";
            case STARTING: return "Starting";
            case RESTARTING: return "Restarting";
            default: return "Unavailable";
        }
    }

    private void startActionBarUpdater() {
        server.getScheduler().buildTask(pluginInstance, () -> {
            for (Map.Entry<UUID, Set<String>> entry : playerQueues.entrySet()) {
                UUID playerId = entry.getKey();
                Set<String> queues = entry.getValue();

                if (queues.isEmpty()) continue;
                String serverName = queues.iterator().next(); // نمایش اولین صفی که توش حضور دارن

                Optional<Player> optPlayer = server.getPlayer(playerId);
                if (optPlayer.isEmpty()) continue;

                Player player = optPlayer.get();
                int position = getPositionInQueue(playerId, serverName);
                if (position == -1) continue;

                Addons.ServerStatus status = addons.getCurrentStatusPublic(serverName);
                if (status == Addons.ServerStatus.ONLINE) {
                    long remainingTime = getEstimatedWaitSeconds(playerId, serverName);
                    String actionBarMsg = configManager.getActionBarMessage()
                            .replace("{pos}", String.valueOf(position))
                            .replace("{time}", String.valueOf(remainingTime))
                            .replace("{server}", serverName);
                    sendActionBar(player, actionBarMsg);
                } else {
                    String reason = getReasonString(status);
                    String actionBarMsg = configManager.getServerCantConnectActionBar()
                            .replace("{pos}", String.valueOf(position))
                            .replace("{Reason}", reason)
                            .replace("{server}", serverName);
                    sendActionBar(player, actionBarMsg);
                }
            }
        }).repeat(500, TimeUnit.MILLISECONDS).schedule();
    }

    private int getPositionInQueue(UUID playerId, String serverName) {
        Queue<UUID> queue = serverQueues.get(serverName);
        if (queue == null) return -1;

        int position = 1;
        for (UUID id : queue) {
            if (id.equals(playerId)) return position;
            position++;
        }
        return -1;
    }

    private int getZeroBasedPositionInQueue(UUID playerId, String serverName) {
        Queue<UUID> queue = serverQueues.get(serverName);
        if (queue == null) return -1;

        int position = 0;
        for (UUID id : queue) {
            if (id.equals(playerId)) return position;
            position++;
        }
        return -1;
    }

    private long getEstimatedWaitSeconds(UUID playerId, String serverName) {
        int zeroBasedPosition = getZeroBasedPositionInQueue(playerId, serverName);
        if (zeroBasedPosition == -1) return 0;

        long now = System.currentTimeMillis();

        if (addons.getCurrentStatusPublic(serverName) != Addons.ServerStatus.ONLINE) {
            return 0L;
        }

        long nextReadyTime = nextQueueReadyTime.getOrDefault(serverName, now);
        long timeUntilHeadCanConnect = Math.max(0L, nextReadyTime - now);
        long estimatedMillis = timeUntilHeadCanConnect + (zeroBasedPosition * getQueueDelayMillis());

        return (estimatedMillis + 999L) / 1000L;
    }

    private long getQueueDelayMillis() {
        return TimeUnit.SECONDS.toMillis(Math.max(0, configManager.getQueueSec()));
    }

    private void startQueueProcessor() {
        server.getScheduler().buildTask(pluginInstance, () -> {
            for (Map.Entry<String, Queue<UUID>> entry : serverQueues.entrySet()) {
                String serverName = entry.getKey();
                Queue<UUID> queue = entry.getValue();

                if (queue.isEmpty()) {
                    nextQueueReadyTime.remove(serverName);
                    continue;
                }

                Optional<RegisteredServer> targetServer = server.getServer(serverName);
                if (targetServer.isEmpty()) continue;

                if (!addons.canConnectToServer(serverName)) {
                    nextQueueReadyTime.remove(serverName);
                    continue;
                }

                if (!nextQueueReadyTime.containsKey(serverName)) {
                    nextQueueReadyTime.put(serverName, System.currentTimeMillis() + getQueueDelayMillis());
                }

                UUID nextPlayerId = queue.peek();
                if (nextPlayerId == null) continue;

                Optional<Player> optPlayer = server.getPlayer(nextPlayerId);
                if (optPlayer.isEmpty()) {
                    queue.poll();
                    Set<String> pq = playerQueues.get(nextPlayerId);
                    if (pq != null) {
                        pq.remove(serverName);
                        if (pq.isEmpty()) playerQueues.remove(nextPlayerId);
                    }
                    nextQueueReadyTime.put(serverName, System.currentTimeMillis());
                    continue;
                }

                Player nextPlayer = optPlayer.get();
                long now = System.currentTimeMillis();
                long readyTime = nextQueueReadyTime.getOrDefault(serverName, now);

                if (now >= readyTime) {
                    queue.poll();
                    Set<String> pq = playerQueues.get(nextPlayerId);
                    if (pq != null) {
                        pq.remove(serverName);
                        if (pq.isEmpty()) playerQueues.remove(nextPlayerId);
                    }

                    if (queue.isEmpty()) {
                        nextQueueReadyTime.remove(serverName);
                    } else {
                        nextQueueReadyTime.put(serverName, now + getQueueDelayMillis());
                    }

                    sendMessage(nextPlayer, "&aYou are now connecting to " + serverName + "...");

                    connectingViaQueue.put(nextPlayerId, true);
                    nextPlayer.createConnectionRequest(targetServer.get()).connect()
                            .whenComplete((result, throwable) -> {
                                connectingViaQueue.remove(nextPlayerId);
                                handleConnectionResult(nextPlayer, result, throwable);
                            });

                    logger.info("{} is now connecting to {}", nextPlayer.getUsername(), serverName);
                }
            }
        }).repeat(500, TimeUnit.MILLISECONDS).schedule();
    }

    private void handleConnectionResult(Player player, ConnectionRequestBuilder.Result result, Throwable throwable) {
        if (throwable != null) {
            String reason = throwable.getMessage() != null ? throwable.getMessage() : "Unknown Connection Error";
            String msg = configManager.getServerCantConnectOthersMessage().replace("{1}", reason);
            sendMessage(player, msg);
            return;
        }

        if (result != null && !result.isSuccessful()) {
            String reasonStr = result.getStatus().name();

            if (result.getReasonComponent().isPresent()) {
                reasonStr = PlainTextComponentSerializer.plainText().serialize(result.getReasonComponent().get());

                if (reasonStr.contains("multiplayer.disconnect.banned")) {
                    reasonStr = "You are banned from this server";
                } else if (reasonStr.contains("multiplayer.disconnect.not_whitelisted")) {
                    reasonStr = "You are not whitelisted on this server";
                }
            }

            String msg = configManager.getServerCantConnectOthersMessage().replace("{1}", reasonStr);
            sendMessage(player, msg);
        }
    }
}