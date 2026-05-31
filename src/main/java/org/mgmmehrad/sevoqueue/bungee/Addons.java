package org.mgmmehrad.sevoqueue.bungee;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class Addons {
    private final Plugin plugin;
    private final ProxyServer proxy;

    private final Map<String, ServerStatus> serverStatus = new ConcurrentHashMap<>();
    private final Map<String, Long> serverOfflineTime = new ConcurrentHashMap<>();
    private final Map<String, Long> serverRestartTime = new ConcurrentHashMap<>();

    private static final long RESTART_TIMEOUT = 120;

    public enum ServerStatus {
        ONLINE,
        FULL,
        OFFLINE,
        STARTING,
        RESTARTING
    }

    public Addons(Plugin plugin) {
        this.plugin = plugin;
        this.proxy = plugin.getProxy();
        startStatusChecker();
    }

    private void startStatusChecker() {
        proxy.getScheduler().schedule(plugin, () -> {
            for (ServerInfo serverInfo : proxy.getServers().values()) {
                refreshServerStatus(serverInfo);
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    public void refreshServerStatus(ServerInfo serverInfo) {
        String serverName = serverInfo.getName();
        ServerStatus oldStatus = serverStatus.getOrDefault(serverName, ServerStatus.OFFLINE);
        ServerStatus newStatus = checkServerStatus(serverInfo, serverName);

        if (oldStatus != newStatus) {
            serverStatus.put(serverName, newStatus);
            plugin.getLogger().info("Server " + serverName + " status: " + oldStatus + " -> " + newStatus);
            onStatusChange(serverName, oldStatus, newStatus);
        } else {
            serverStatus.put(serverName, newStatus);
        }
    }

    private ServerStatus checkServerStatus(ServerInfo serverInfo, String serverName) {
        Long restartTime = serverRestartTime.get(serverName);
        if (restartTime != null) {
            long elapsed = (System.currentTimeMillis() - restartTime) / 1000;
            if (elapsed < RESTART_TIMEOUT) {
                return ServerStatus.RESTARTING;
            }
            serverRestartTime.remove(serverName);
        }

        Long offlineTime = serverOfflineTime.get(serverName);
        if (offlineTime != null) {
            long elapsed = (System.currentTimeMillis() - offlineTime) / 1000;
            if (elapsed < RESTART_TIMEOUT) {
                return ServerStatus.STARTING;
            }
            return ServerStatus.OFFLINE;
        }

        return ServerStatus.ONLINE;
    }

    private void onStatusChange(String serverName, ServerStatus oldStatus, ServerStatus newStatus) {
        switch (newStatus) {
            case RESTARTING:
                plugin.getLogger().info("Server " + serverName + " is restarting...");
                break;
            case STARTING:
                plugin.getLogger().info("Server " + serverName + " is starting up...");
                break;
            case ONLINE:
                plugin.getLogger().info("Server " + serverName + " is online!");
                break;
            case FULL:
                plugin.getLogger().info("Server " + serverName + " is full!");
                break;
            case OFFLINE:
                plugin.getLogger().warning("Server " + serverName + " is offline!");
                break;
        }
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
                return ChatColor.RED + "Server is Full";
            case OFFLINE:
                return ChatColor.RED + "Error: Server Is Offline";
            case STARTING:
                Long offlineTime = serverOfflineTime.get(serverName);
                if (offlineTime != null) {
                    long remaining = RESTART_TIMEOUT - ((System.currentTimeMillis() - offlineTime) / 1000);
                    if (remaining > 0) {
                        return ChatColor.GOLD + "Server Is Starting... (" + ChatColor.YELLOW + remaining + "s" + ChatColor.GOLD + ")";
                    }
                }
                return ChatColor.GOLD + "Server Is Starting...";
            case RESTARTING:
                Long restartTime = serverRestartTime.get(serverName);
                if (restartTime != null) {
                    long remaining = RESTART_TIMEOUT - ((System.currentTimeMillis() - restartTime) / 1000);
                    if (remaining > 0) {
                        return ChatColor.GOLD + "Server is Restarting... (" + ChatColor.YELLOW + remaining + "s" + ChatColor.GOLD + ")";
                    }
                }
                return ChatColor.GOLD + "Server is Restarting...";
            default:
                return ChatColor.RED + "Error: Unknown server status";
        }
    }

    public String getAlreadyConnectedMessage(String serverName) {
        return ChatColor.RED + "Error: You Already Connected To This Server";
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

        ServerInfo serverInfo = proxy.getServerInfo(serverName);
        if (serverInfo != null) {
            return ServerStatus.ONLINE;
        }
        return ServerStatus.OFFLINE;
    }
}