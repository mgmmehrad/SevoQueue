package org.mgmmehrad.sevoqueue;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.command.RawCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Plugin(id = "sevoqueue", name = "SevoQueue", version = "1.0.0", authors = {"mgmmehrad"})
public class Main {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private ConfigManager configManager;
    private QueueManager queueManager;
    private PlayerJoinListener joinListener;

    // License System
    private boolean licenseValid = false;
    private String pluginDisplayName = "SevoQueue";

    @Inject
    public Main(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        configManager = new ConfigManager(dataDirectory, logger);
        configManager.loadConfig();
        checkLicenseAndUpdateName();

        if (!configManager.isEnabled()) {
            logger.info(pluginDisplayName + " is disabled in config.yml");
            return;
        }

        queueManager = new QueueManager(server, logger, configManager, this);
        joinListener = new PlayerJoinListener(server, logger, queueManager, configManager);

        // Register listeners
        server.getEventManager().register(this, joinListener);

        // Register server status listener
        server.getEventManager().register(this, queueManager.getAddons());

        CommandManager commandManager = server.getCommandManager();

        // Register /queue command
        CommandMeta queueMeta = commandManager.metaBuilder("queue")
                .plugin(this)
                .build();
        commandManager.register(queueMeta, new QueueCommand(queueManager, configManager));

        // Register /leavequeue command
        CommandMeta leaveQueueMeta = commandManager.metaBuilder("leavequeue")
                .plugin(this)
                .build();
        commandManager.register(leaveQueueMeta, new LeaveQueueCommand(queueManager));

        // Register /sevoqueue command (reload)
        CommandMeta sevoqueueMeta = commandManager.metaBuilder("sevoqueue")
                .aliases("svq")
                .plugin(this)
                .build();
        commandManager.register(sevoqueueMeta, new SevoQueueReloadCommand());

        // Register /send command
        CommandMeta sendMeta = commandManager.metaBuilder("send")
                .plugin(this)
                .build();
        commandManager.register(sendMeta, new SendCommand());

        if (configManager.isVelocityServerCommand()) {
            try {
                commandManager.unregister("server");
            } catch (Exception ignored) {
            }

            // Register /server command
            CommandMeta serverMeta = commandManager.metaBuilder("server")
                    .plugin(this)
                    .build();
            commandManager.register(serverMeta, new ServerCommand());
        }

        if (configManager.isSlashServer()) {
            registerSlashServerCommands();
            logger.info("SlashServer commands registered!");
        }

        sendLicenseMessage();

        logger.info("=========================================");
        logger.info(pluginDisplayName + " v1.0.0 Enabled!");
        logger.info("Queue time: {} seconds", configManager.getQueueSec());
        logger.info("Slash Server: {}", configManager.isSlashServer() ? "Enabled" : "Disabled");
        logger.info("=========================================");
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
            logger.info("License validated! Running SevoQueuePlus with full features.");
        } else if (licenseEnabled) {
            licenseValid = false;
            pluginDisplayName = "SevoQueue";
            logger.warn("Invalid license code! Please check your Licence-Code in config.yml");
        } else {
            licenseValid = false;
            pluginDisplayName = "SevoQueue";
            logger.info("License feature is disabled. Running standard SevoQueue.");
        }
    }

    private void sendLicenseMessage() {
        if (licenseValid) {
            logger.info("=========================================");
            logger.info("✓ SevoQueuePlus License Activated!");
            logger.info("✓ Full features are now available");
            logger.info("=========================================");
        }
    }

    public boolean hasFullFeatures() {
        return licenseValid;
    }

    public String getPluginDisplayName() {
        return pluginDisplayName;
    }

    // ==================== LeaveQueue Command ====================
    private class LeaveQueueCommand implements SimpleCommand {
        private final QueueManager queueManager;

        public LeaveQueueCommand(QueueManager queueManager) {
            this.queueManager = queueManager;
        }

        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();

            if (!(source instanceof Player)) {
                source.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
                return;
            }

            Player player = (Player) source;
            queueManager.removeFromQueue(player);
        }
    }

    // ==================== SevoQueue Reload Command ====================
    private class SevoQueueReloadCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] args = invocation.arguments();

            if (!configManager.hasPermission(source, "reload")) {
                source.sendMessage(Component.text("You don't have permission!", NamedTextColor.RED));
                return;
            }

            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                configManager.loadConfig();
                checkLicenseAndUpdateName();
                source.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&a" + pluginDisplayName + " config reloaded successfully!"));
                logger.info("Config reloaded by {}", source);
            } else {
                source.sendMessage(Component.text("Usage: /sevoqueue reload", NamedTextColor.YELLOW));
            }
        }

        @Override
        public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
            String[] args = invocation.arguments();
            List<String> suggestions = new ArrayList<>();
            if (args.length == 1 && "reload".startsWith(args[0].toLowerCase())) {
                suggestions.add("reload");
            }
            return CompletableFuture.completedFuture(suggestions);
        }
    }

    // ==================== Send Command ====================
    private class SendCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] args = invocation.arguments();

            if (!configManager.hasPermission(source, "send")) {
                source.sendMessage(Component.text("You don't have permission!", NamedTextColor.RED));
                return;
            }

            if (args.length < 2) {
                source.sendMessage(Component.text("Usage: /send <player|all> <server>", NamedTextColor.YELLOW));
                return;
            }

            String playerName = args[0];
            String serverName = args[1];

            Optional<RegisteredServer> targetServer = server.getServer(serverName);
            if (targetServer.isEmpty()) {
                source.sendMessage(Component.text("Server not found!", NamedTextColor.RED));
                return;
            }

            if (playerName.equalsIgnoreCase("all")) {
                Collection<Player> allPlayers = server.getAllPlayers();
                int sentCount = 0;

                for (Player player : allPlayers) {
                    boolean alreadyThere = player.getCurrentServer()
                            .map(connection -> connection.getServerInfo().getName().equalsIgnoreCase(targetServer.get().getServerInfo().getName()))
                            .orElse(false);
                    if (alreadyThere) {
                        continue;
                    }

                    sendPlayerToServer(player, targetServer.get(), serverName, source);
                    sentCount++;
                }

                source.sendMessage(Component.text("Sent " + sentCount + " player(s) to " + serverName + " queue", NamedTextColor.GREEN));
                return;
            }

            Optional<Player> targetPlayer = server.getPlayer(playerName);
            if (targetPlayer.isEmpty()) {
                source.sendMessage(Component.text("Player not found!", NamedTextColor.RED));
                return;
            }

            Player player = targetPlayer.get();
            sendPlayerToServer(player, targetServer.get(), serverName, source);
            source.sendMessage(Component.text("Sent " + playerName + " to " + serverName + " queue", NamedTextColor.GREEN));
            player.sendMessage(Component.text("You were sent to the queue for " + serverName + " by " + source, NamedTextColor.YELLOW));
        }

        private void sendPlayerToServer(Player player, RegisteredServer targetServer, String serverName, CommandSource source) {
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
                if ("all".startsWith(args[0].toLowerCase())) {
                    suggestions.add("all");
                }
                for (Player player : server.getAllPlayers()) {
                    if (player.getUsername().toLowerCase().startsWith(args[0].toLowerCase())) {
                        suggestions.add(player.getUsername());
                    }
                }
            } else if (args.length == 2) {
                for (RegisteredServer s : server.getAllServers()) {
                    String serverName = s.getServerInfo().getName();
                    if (serverName.toLowerCase().startsWith(args[1].toLowerCase())) {
                        suggestions.add(serverName);
                    }
                }
            }

            return CompletableFuture.completedFuture(suggestions);
        }
    }

    // ==================== Server Command ====================
    private class ServerCommand implements SimpleCommand {
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

            if (queueManager.isInQueue(player)) {
                player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cYou are already in a queue! Use &e/queue leave &cfirst."));
                return;
            }

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

    // ==================== SlashServer Commands ====================
    private void registerSlashServerCommands() {
        Collection<RegisteredServer> servers = server.getAllServers();

        for (RegisteredServer registeredServer : servers) {
            String serverName = registeredServer.getServerInfo().getName();

            CommandManager commandManager = server.getCommandManager();
            try {
                commandManager.unregister(serverName);
            } catch (Exception ignored) {
            }

            CommandMeta commandMeta = commandManager.metaBuilder(serverName)
                    .plugin(this)
                    .build();

            commandManager.register(commandMeta, new RawCommand() {
                @Override
                public void execute(RawCommand.Invocation invocation) {
                    if (!(invocation.source() instanceof Player)) {
                        invocation.source().sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
                        return;
                    }

                    Player player = (Player) invocation.source();

                    if (queueManager.isInQueue(player)) {
                        player.sendMessage(Component.text("You are already in a queue! Use /queue leave first.", NamedTextColor.RED));
                        return;
                    }

                    queueManager.addToQueue(player, serverName);
                }
            });

            logger.info("Registered slash command: /{}", serverName);
        }
    }
}