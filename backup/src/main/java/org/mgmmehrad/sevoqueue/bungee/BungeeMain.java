package org.mgmmehrad.sevoqueue.bungee;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public class BungeeMain extends Plugin implements Listener {
    private Configuration config;
    private final Map<String, Queue<UUID>> serverQueues = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerQueue = new ConcurrentHashMap<>();
    private final Map<String, Long> nextQueueTime = new ConcurrentHashMap<>();
    private int queueSec = 5;
    private String queueMessage = "&aYou are in position &e{pos} &aYour remaining time is &e{time} &aseconds";
    private String actionBarMessage = "&eQueue: &a{server} &7| &ePosition: &a{pos} &7| &eTime: &a{time}s";
    private boolean enabled = true;
    private String defaultServer = "lobby";

    // License System
    private boolean licenseValid = false;
    private String pluginDisplayName = "SevoQueue";

    @Override
    public void onEnable() {
        loadConfig();
        checkLicenseAndUpdateName();

        if (!enabled) {
            getLogger().info(pluginDisplayName + " is disabled in config.yml");
            return;
        }

        getProxy().getPluginManager().registerListener(this, this);
        registerCommands();
        startQueueProcessor();
        startActionBarUpdater();

        sendLicenseMessage();

        getLogger().info("=========================================");
        getLogger().info(pluginDisplayName + " v1.0.0 Enabled (BungeeCord Mode)!");
        getLogger().info("Queue time: " + queueSec + " seconds");
        getLogger().info("=========================================");
    }

    @Override
    public void onDisable() {
        getLogger().info(pluginDisplayName + " Disabled!");
    }

    private void loadConfig() {
        try {
            if (!getDataFolder().exists()) {
                getDataFolder().mkdir();
            }

            File configFile = new File(getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                try (InputStream in = getResourceAsStream("config.yml")) {
                    if (in != null) {
                        Files.copy(in, configFile.toPath());
                    }
                }
            }

            config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);

            enabled = config.getBoolean("Enabled", true);
            queueSec = Math.max(0, config.getInt("queue-sec", 5));
            queueMessage = config.getString("Queue-Message", "&aYou are in position &e{pos} &aYour remaining time is &e{time} &aseconds");
            actionBarMessage = config.getString("ActionBar-Message", "&eQueue: &a{server} &7| &ePosition: &a{pos} &7| &eTime: &a{time}s");
            defaultServer = config.getString("default-server", "lobby");

        } catch (Exception e) {
            getLogger().severe("Failed to load config: " + e.getMessage());
        }
    }

    // ==================== License System ====================
    private boolean checkLicense(String licenseKey) {
        if (licenseKey == null) return false;

        String validLicense1 = "Qwfgwknfnifldm@fgteWdf3#d)";
        String validLicense2 = "&24jf3jfneifBjaHFBEINJJNBY3#5%*(";

        return licenseKey.equals(validLicense1) || licenseKey.equals(validLicense2);
    }

    private void checkLicenseAndUpdateName() {
        boolean licenseEnabled = config.getBoolean("licence", false);
        String licenseKey = config.getString("Licence-Code", "");

        if (licenseEnabled && checkLicense(licenseKey)) {
            licenseValid = true;
            pluginDisplayName = "SevoQueuePlus";
            getLogger().info("License validated! Running SevoQueuePlus with full features.");
        } else if (licenseEnabled) {
            licenseValid = false;
            pluginDisplayName = "SevoQueue";
            getLogger().warning("Invalid license code! Please check your Licence-Code in config.yml");
        } else {
            licenseValid = false;
            pluginDisplayName = "SevoQueue";
            getLogger().info("License feature is disabled. Running standard SevoQueue.");
        }
    }

    private void sendLicenseMessage() {
        if (licenseValid) {
            getLogger().info("=========================================");
            getLogger().info("✓ SevoQueuePlus License Activated!");
            getLogger().info("✓ Full features are now available");
            getLogger().info("=========================================");
        }
    }

    public boolean hasFullFeatures() {
        return licenseValid;
    }

    private void registerCommands() {
        getProxy().getPluginManager().registerCommand(this, new BungeeQueueCommand());
        getProxy().getPluginManager().registerCommand(this, new BungeeServerCommand());

        if (config.getBoolean("slash-server", true)) {
            for (String serverName : getProxy().getServers().keySet()) {
                getProxy().getPluginManager().registerCommand(this, new BungeeSlashCommand(serverName));
                getLogger().info("Registered slash command: /" + serverName);
            }
        }
    }

    private void startQueueProcessor() {
        getProxy().getScheduler().schedule(this, () -> {
            for (Map.Entry<String, Queue<UUID>> entry : serverQueues.entrySet()) {
                String serverName = entry.getKey();
                Queue<UUID> queue = entry.getValue();

                if (queue == null || queue.isEmpty()) {
                    nextQueueTime.remove(serverName);
                    continue;
                }

                UUID nextId = queue.peek();
                if (nextId == null) continue;

                ProxiedPlayer player = getProxy().getPlayer(nextId);
                if (player == null || !player.isConnected()) {
                    queue.poll();
                    playerQueue.remove(nextId);
                    continue;
                }

                if (player.getServer() != null && player.getServer().getInfo().getName().equals(serverName)) {
                    queue.poll();
                    playerQueue.remove(nextId);
                    continue;
                }

                long now = System.currentTimeMillis();
                long readyTime = nextQueueTime.getOrDefault(serverName, now);

                if (now >= readyTime) {
                    queue.poll();
                    playerQueue.remove(nextId);

                    if (queue.isEmpty()) {
                        nextQueueTime.remove(serverName);
                    } else {
                        nextQueueTime.put(serverName, now + (queueSec * 1000L));
                    }

                    player.sendMessage(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&',
                            "&aYou are now connecting to " + serverName + "..."));

                    player.connect(getProxy().getServerInfo(serverName));
                    getLogger().info(player.getName() + " is now connecting to " + serverName);
                }
            }
        }, 500, 500, TimeUnit.MILLISECONDS);
    }

    private void startActionBarUpdater() {
        getProxy().getScheduler().schedule(this, () -> {
            for (Map.Entry<UUID, String> entry : playerQueue.entrySet()) {
                UUID id = entry.getKey();
                String serverName = entry.getValue();
                ProxiedPlayer player = getProxy().getPlayer(id);
                if (player == null) continue;

                Queue<UUID> queue = serverQueues.get(serverName);
                if (queue == null) continue;

                int position = 1;
                for (UUID qId : queue) {
                    if (qId.equals(id)) break;
                    position++;
                }

                long waitTime = getEstimatedWaitSeconds(id, serverName);

                String msg = actionBarMessage
                        .replace("{pos}", String.valueOf(position))
                        .replace("{time}", String.valueOf(waitTime))
                        .replace("{server}", serverName);

                sendActionBar(player, msg);
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    private long getEstimatedWaitSeconds(UUID id, String serverName) {
        Queue<UUID> queue = serverQueues.get(serverName);
        if (queue == null) return 0;

        int position = 0;
        for (UUID qId : queue) {
            if (qId.equals(id)) break;
            position++;
        }

        long now = System.currentTimeMillis();
        long nextTime = nextQueueTime.getOrDefault(serverName, now);
        long remaining = Math.max(0, nextTime - now);
        return (remaining + (position * queueSec * 1000L)) / 1000L;
    }

    private void sendActionBar(ProxiedPlayer player, String message) {
        String colored = net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', message);
        player.sendMessage(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(colored));
    }

    public boolean canBypass(ProxiedPlayer player) {
        return player.hasPermission("sevoqueue.bypass");
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();
        if (getProxy().getServerInfo(defaultServer) != null) {
            player.connect(getProxy().getServerInfo(defaultServer));
            player.sendMessage(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&',
                    "&aWelcome! You have been connected to &e" + defaultServer));
        }
    }

    @EventHandler
    public void onServerConnect(ServerConnectEvent event) {
        ProxiedPlayer player = event.getPlayer();
        String targetServer = event.getTarget().getName();

        if (playerQueue.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&',
                    "&cYou are in queue! Use /queue leave first."));
            return;
        }

        if (canBypass(player)) return;

        if (player.getServer() != null) {
            event.setCancelled(true);
            addToQueue(player, targetServer);
        }
    }

    private void addToQueue(ProxiedPlayer player, String serverName) {
        UUID id = player.getUniqueId();

        if (playerQueue.containsKey(id)) {
            player.sendMessage(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&',
                    "&cYou are already in a queue!"));
            return;
        }

        serverQueues.computeIfAbsent(serverName, k -> new ConcurrentLinkedQueue<>());
        Queue<UUID> queue = serverQueues.get(serverName);
        boolean wasEmpty = queue.isEmpty();

        queue.add(id);
        playerQueue.put(id, serverName);

        if (wasEmpty) {
            nextQueueTime.put(serverName, System.currentTimeMillis() + (queueSec * 1000L));
        }

        String msg = queueMessage
                .replace("{pos}", String.valueOf(queue.size()))
                .replace("{time}", String.valueOf(queueSec));
        player.sendMessage(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', msg));
        getLogger().info(player.getName() + " added to queue for " + serverName);
    }

    private void removeFromQueue(ProxiedPlayer player) {
        UUID id = player.getUniqueId();
        String server = playerQueue.remove(id);
        if (server != null) {
            Queue<UUID> queue = serverQueues.get(server);
            if (queue != null) queue.remove(id);
            player.sendMessage(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', "&aYou left the queue!"));
        } else {
            player.sendMessage(net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', "&cYou are not in any queue!"));
        }
    }

    // ==================== Command Classes ====================

    private class BungeeQueueCommand extends Command {
        public BungeeQueueCommand() {
            super("queue");
        }

        @Override
        public void execute(net.md_5.bungee.api.CommandSender sender, String[] args) {
            if (!(sender instanceof ProxiedPlayer)) return;
            ProxiedPlayer p = (ProxiedPlayer) sender;

            if (args.length == 0) {
                sendHelp(p);
                return;
            }

            switch (args[0].toLowerCase()) {
                case "join":
                    if (args.length > 1) {
                        addToQueue(p, args[1]);
                    } else {
                        p.sendMessage(net.md_5.bungee.api.ChatColor.RED + "Usage: /queue join <server>");
                    }
                    break;
                case "leave":
                    removeFromQueue(p);
                    break;
                default:
                    sendHelp(p);
            }
        }

        private void sendHelp(ProxiedPlayer p) {
            p.sendMessage(net.md_5.bungee.api.ChatColor.GOLD + "===== " + pluginDisplayName + " Help =====");
            p.sendMessage(net.md_5.bungee.api.ChatColor.YELLOW + "/queue join <server> - Join queue");
            p.sendMessage(net.md_5.bungee.api.ChatColor.YELLOW + "/queue leave - Leave queue");
        }
    }

    private class BungeeServerCommand extends Command {
        public BungeeServerCommand() {
            super("server");
        }

        @Override
        public void execute(net.md_5.bungee.api.CommandSender sender, String[] args) {
            if (!(sender instanceof ProxiedPlayer)) return;
            ProxiedPlayer p = (ProxiedPlayer) sender;

            if (args.length == 0) {
                p.sendMessage(net.md_5.bungee.api.ChatColor.RED + "Usage: /server <name>");
                return;
            }

            if (playerQueue.containsKey(p.getUniqueId())) {
                p.sendMessage(net.md_5.bungee.api.ChatColor.RED + "You are already in a queue!");
                return;
            }

            addToQueue(p, args[0]);
        }
    }

    private class BungeeSlashCommand extends Command {
        private final String target;

        public BungeeSlashCommand(String target) {
            super(target);
            this.target = target;
        }

        @Override
        public void execute(net.md_5.bungee.api.CommandSender sender, String[] args) {
            if (!(sender instanceof ProxiedPlayer)) return;
            ProxiedPlayer p = (ProxiedPlayer) sender;

            if (playerQueue.containsKey(p.getUniqueId())) {
                p.sendMessage(net.md_5.bungee.api.ChatColor.RED + "You are already in a queue!");
                return;
            }

            addToQueue(p, target);
        }
    }
}