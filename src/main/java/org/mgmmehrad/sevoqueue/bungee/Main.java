package org.mgmmehrad.sevoqueue.bungee;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

public class Main extends Plugin {
    private static Main instance;
    private ConfigManager configManager;
    private QueueManager queueManager;
    private PlayerJoinListener joinListener;
    private Addons addons;
    private ScheduledTask statusTask;

    private boolean licenseValid = false;
    private String pluginDisplayName = "SevoQueue";

    @Override
    public void onEnable() {
        instance = this;

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        configManager = new ConfigManager(this);
        configManager.loadConfig();
        checkLicenseAndUpdateName();

        if (!configManager.isEnabled()) {
            getLogger().info(pluginDisplayName + " is disabled in config.yml");
            return;
        }

        addons = new Addons(this);
        queueManager = new QueueManager(this, configManager, addons);
        joinListener = new PlayerJoinListener(this, queueManager, configManager);

        // Register listener
        getProxy().getPluginManager().registerListener(this, joinListener);

        // Register commands
        getProxy().getPluginManager().registerCommand(this, new QueueCommand(queueManager, configManager));
        getProxy().getPluginManager().registerCommand(this, new SevoQueueReloadCommand());
        getProxy().getPluginManager().registerCommand(this, new SendCommand());

        if (configManager.isVelocityServerCommand()) {
            getProxy().getPluginManager().registerCommand(this, new ServerCommand(queueManager, configManager));
        }

        if (configManager.isSlashServer()) {
            registerSlashServerCommands();
            getLogger().info("SlashServer commands registered!");
        }

        // Start status checker
        startStatusChecker();

        sendLicenseMessage();

        getLogger().info("=========================================");
        getLogger().info(pluginDisplayName + " v1.0.0 Enabled!");
        getLogger().info("Running on: BungeeCord");
        getLogger().info("Queue time: " + configManager.getQueueSec() + " seconds");
        getLogger().info("Slash Server: " + (configManager.isSlashServer() ? "Enabled" : "Disabled"));
        getLogger().info("=========================================");
    }

    @Override
    public void onDisable() {
        if (statusTask != null) {
            statusTask.cancel();
        }
        getLogger().info(pluginDisplayName + " shutting down...");
    }

    private void startStatusChecker() {
        statusTask = getProxy().getScheduler().schedule(this, () -> {
            for (ServerInfo serverInfo : getProxy().getServers().values()) {
                addons.refreshServerStatus(serverInfo);
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    private void registerSlashServerCommands() {
        Collection<ServerInfo> servers = getProxy().getServers().values();

        for (ServerInfo serverInfo : servers) {
            String serverName = serverInfo.getName();

            getProxy().getPluginManager().registerCommand(this, new Command(serverName) {
                @Override
                public void execute(CommandSender sender, String[] args) {
                    if (!(sender instanceof ProxiedPlayer)) {
                        sender.sendMessage(new TextComponent(ChatColor.RED + "Only players can use this command!"));
                        return;
                    }

                    ProxiedPlayer player = (ProxiedPlayer) sender;

                    if (!configManager.hasPermission(player, "slashserver") && !queueManager.canBypassQueue(player)) {
                        player.sendMessage(new TextComponent(ChatColor.RED + "You don't have permission!"));
                        return;
                    }

                    if (queueManager.isInQueue(player)) {
                        player.sendMessage(new TextComponent(ChatColor.RED + "You are already in a queue! Use /queue leave first."));
                        return;
                    }

                    queueManager.addToQueue(player, serverName);
                }
            });

            getLogger().info("Registered slash command: /" + serverName);
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
        boolean licenseEnabled = configManager.isLicenseEnabled();
        String licenseKey = configManager.getLicenseCode();

        if (licenseEnabled && checkLicense(licenseKey)) {
            licenseValid = true;
            pluginDisplayName = "SevoQueuePlus";
            getLogger().info("License validated! Running SevoQueuePlus with full features.");
        } else if (licenseEnabled) {
            licenseValid = false;
            pluginDisplayName = "SevoQueue";
            getLogger().warning("Invalid license code! Please check your Licence-Code in config.yml");
            getLogger().warning("Valid licenses are:");
            getLogger().warning("  - Qwfgwknfnifldm@fgteWdf3#d)");
            getLogger().warning("  - &24jf3jfneifBjaHFBEINJJNBY3#5%*(");
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

    public String getPluginDisplayName() {
        return pluginDisplayName;
    }

    public static Main getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public QueueManager getQueueManager() {
        return queueManager;
    }

    public Addons getAddons() {
        return addons;
    }

    // ==================== SevoQueue Reload Command ====================
    private class SevoQueueReloadCommand extends Command {
        public SevoQueueReloadCommand() {
            super("sevoqueue", null, "svq");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!configManager.hasPermission(sender, "reload")) {
                sender.sendMessage(new TextComponent(ChatColor.RED + "You don't have permission!"));
                return;
            }

            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                configManager.loadConfig();
                checkLicenseAndUpdateName();
                sender.sendMessage(new TextComponent(ChatColor.GREEN + pluginDisplayName + " config reloaded successfully!"));
                getLogger().info("Config reloaded by " + sender.getName());
            } else {
                sender.sendMessage(new TextComponent(ChatColor.YELLOW + "Usage: /sevoqueue reload"));
            }
        }
    }

    // ==================== Send Command ====================
    private class SendCommand extends Command {
        public SendCommand() {
            super("send");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!configManager.hasPermission(sender, "send")) {
                sender.sendMessage(new TextComponent(ChatColor.RED + "You don't have permission!"));
                return;
            }

            if (args.length < 2) {
                sender.sendMessage(new TextComponent(ChatColor.YELLOW + "Usage: /send <player> <server>"));
                return;
            }

            String playerName = args[0];
            String serverName = args[1];

            ProxiedPlayer targetPlayer = ProxyServer.getInstance().getPlayer(playerName);
            if (targetPlayer == null) {
                sender.sendMessage(new TextComponent(ChatColor.RED + "Player not found!"));
                return;
            }

            ServerInfo targetServer = ProxyServer.getInstance().getServerInfo(serverName);
            if (targetServer == null) {
                sender.sendMessage(new TextComponent(ChatColor.RED + "Server not found!"));
                return;
            }

            if (queueManager.isInQueue(targetPlayer)) {
                queueManager.removeFromQueue(targetPlayer);
            }

            queueManager.setConnectingViaQueue(targetPlayer, true);
            targetPlayer.connect(targetServer);
            queueManager.setConnectingViaQueue(targetPlayer, false);

            sender.sendMessage(new TextComponent(ChatColor.GREEN + "Sent " + playerName + " to " + serverName));
            targetPlayer.sendMessage(new TextComponent(ChatColor.YELLOW + "You were teleported to " + serverName + " by " + sender.getName()));
        }
    }
}