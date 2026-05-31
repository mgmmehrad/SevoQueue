package org.mgmmehrad.sevoqueue;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class Addons {
    private final ProxyServer server;
    private final Logger logger;
    private final Object pluginInstance;

    private final Map<String, ServerStatus> serverStatus = new ConcurrentHashMap<>();
    private final Map<String, Long> serverOfflineTime = new ConcurrentHashMap<>();
    private final Map<String, Long> serverRestartTime = new ConcurrentHashMap<>();

    private static final long RESTART_TIMEOUT = 120;
    private static final long CHECK_INTERVAL = 5;

    public enum ServerStatus {
        ONLINE,
        FULL,
        OFFLINE,
        STARTING,
        RESTARTING
    }

    public Addons(ProxyServer server, Logger logger, Object pluginInstance) {
        this.server = server;
        this.logger = logger;
        this.pluginInstance = pluginInstance;
        startStatusChecker();
    }

    private void startStatusChecker() {
        server.getScheduler().buildTask(pluginInstance, () -> {
            for (RegisteredServer rs : server.getAllServers()) {
                String serverName = rs.getServerInfo().getName();
                refreshServerStatus(rs, serverName);
            }
        }).repeat(CHECK_INTERVAL, TimeUnit.SECONDS).schedule();
    }

    private ServerStatus refreshServerStatus(RegisteredServer rs, String serverName) {
        ServerStatus oldStatus = serverStatus.getOrDefault(serverName, ServerStatus.OFFLINE);
        ServerStatus newStatus = checkServerStatus(rs, serverName);

        if (oldStatus != newStatus) {
            serverStatus.put(serverName, newStatus);
            logger.info("Server {} status: {} -> {}", serverName, oldStatus, newStatus);
            onStatusChange(serverName, oldStatus, newStatus);
        } else {
            serverStatus.put(serverName, newStatus);
        }

        return newStatus;
    }

    private ServerStatus checkServerStatus(RegisteredServer rs, String serverName) {
        ServerStatus pingStatus = pingServerStatus(rs, serverName);
        if (pingStatus != null) {
            serverOfflineTime.remove(serverName);
            serverRestartTime.remove(serverName);
            return pingStatus;
        }

        Long restartTime = serverRestartTime.get(serverName);
        if (restartTime != null) {
            long elapsed = (System.currentTimeMillis() - restartTime) / 1000;
            if (elapsed < RESTART_TIMEOUT) {
                return ServerStatus.RESTARTING;
            }
            serverRestartTime.remove(serverName);
        }

        Long offlineTime = serverOfflineTime.get(serverName);
        if (offlineTime == null) {
            offlineTime = System.currentTimeMillis();
            serverOfflineTime.put(serverName, offlineTime);
        }

        long elapsed = (System.currentTimeMillis() - offlineTime) / 1000;
        return elapsed < RESTART_TIMEOUT ? ServerStatus.STARTING : ServerStatus.OFFLINE;
    }

    private ServerStatus pingServerStatus(RegisteredServer rs, String serverName) {
        try {
            ServerPing ping = rs.ping().orTimeout(2, TimeUnit.SECONDS).join();
            serverOfflineTime.remove(serverName);
            serverRestartTime.remove(serverName);

            Optional<ServerPing.Players> players = ping.getPlayers();
            if (players.isPresent()) {
                int online = players.get().getOnline();
                int max = players.get().getMax();

                if (max > 0 && online >= max) {
                    return ServerStatus.FULL;
                }
            }

            return ServerStatus.ONLINE;
        } catch (Exception e) {
            return null;
        }
    }

    private void onStatusChange(String serverName, ServerStatus oldStatus, ServerStatus newStatus) {
        switch (newStatus) {
            case RESTARTING:
                logger.info("Server {} is restarting...", serverName);
                break;
            case STARTING:
                logger.info("Server {} is starting up...", serverName);
                break;
            case ONLINE:
                logger.info("Server {} is online!", serverName);
                break;
            case FULL:
                logger.info("Server {} is full!", serverName);
                break;
            case OFFLINE:
                logger.warn("Server {} is offline!", serverName);
                break;
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("SevoQueue Addons shutting down...");
    }

    public void markServerRestarting(String serverName) {
        serverRestartTime.put(serverName, System.currentTimeMillis());
        serverOfflineTime.remove(serverName);
        serverStatus.put(serverName, ServerStatus.RESTARTING);
    }

    public void markServerOffline(String serverName) {
        serverOfflineTime.put(serverName, System.currentTimeMillis());
        serverRestartTime.remove(serverName);
        serverStatus.put(serverName, ServerStatus.OFFLINE);
    }

    public void markServerOnline(String serverName) {
        serverOfflineTime.remove(serverName);
        serverRestartTime.remove(serverName);
        serverStatus.put(serverName, ServerStatus.ONLINE);
    }

    public String getServerStatusMessage(String serverName) {
        ServerStatus status = getCurrentStatus(serverName);

        switch (status) {
            case ONLINE:
                return null;
            case FULL:
                return "&cServer is Full";
            case OFFLINE:
                return "&cError: Server Is Offline";
            case STARTING:
                Long offlineTime = serverOfflineTime.get(serverName);
                if (offlineTime != null) {
                    long remaining = RESTART_TIMEOUT - ((System.currentTimeMillis() - offlineTime) / 1000);
                    if (remaining > 0) {
                        return "&6Server Is Starting... (&e" + remaining + "s&6)";
                    }
                }
                return "&6Server Is Starting...";
            case RESTARTING:
                Long restartTime = serverRestartTime.get(serverName);
                if (restartTime != null) {
                    long remaining = RESTART_TIMEOUT - ((System.currentTimeMillis() - restartTime) / 1000);
                    if (remaining > 0) {
                        return "&6Server is Restarting... (&e" + remaining + "s&6)";
                    }
                }
                return "&6Server is Restarting...";
            default:
                return "&cError: Unknown server status";
        }
    }

    public String getAlreadyConnectedMessage(String serverName) {
        return "&cError: You Already Connected To This Server";
    }

    public boolean canConnectToServer(String serverName) {
        ServerStatus status = getCurrentStatus(serverName);
        return status == ServerStatus.ONLINE;
    }

    private ServerStatus getCurrentStatus(String serverName) {
        ServerStatus status = serverStatus.get(serverName);
        if (status != null) {
            return status;
        }

        Optional<RegisteredServer> registeredServer = server.getServer(serverName);
        return registeredServer
                .map(value -> refreshServerStatus(value, serverName))
                .orElse(ServerStatus.OFFLINE);
    }
}