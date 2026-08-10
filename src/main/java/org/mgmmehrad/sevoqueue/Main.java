package org.mgmmehrad.sevoqueue;

import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
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
import org.aopalliance.intercept.Invocation;
import org.slf4j.Logger;
import org.bstats.velocity.Metrics;

import java.nio.file.Path;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

public class Main {
    private final Object pluginInstance;
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final Metrics.Factory metricsFactory;
    private ConfigManager configManager;
    private QueueManager queueManager;
    private PlayerJoinListener joinListener;
    private Licence licence;
    private String pluginDisplayName = "SevoQueue";

    public Main(Object pluginInstance, ProxyServer server, Logger logger, Path dataDirectory, Metrics.Factory metricsFactory) {
        this.pluginInstance = pluginInstance;
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.metricsFactory = metricsFactory;
    }

    public void initialize(ProxyInitializeEvent event) {
        int pluginId = 32579;
        try {
            metricsFactory.make(pluginInstance, pluginId);
            logger.info("[bStats] Metrics connected successfully", pluginId);
        } catch (Exception e) {
            logger.error("[bStats] Failed to connect to Metrics", pluginId, e.getMessage());
        }

        configManager = new ConfigManager(dataDirectory, logger);
        configManager.loadConfig();

        licence = new Licence(logger);
        if (configManager.isLicenseEnabled()) {
            boolean valid = licence.checkLicense(configManager.getLicenseCode());
            pluginDisplayName = valid ? "SevoQueuePlus" : "SevoQueue";
            if (!valid) {
                logger.warn("Invalid license: {}", licence.getLastMessage());
            }
        } else {
            pluginDisplayName = "SevoQueue";
            logger.info("License feature is disabled. Running standard SevoQueue.");
        }

        if (!configManager.isEnabled()) {
            logger.info(pluginDisplayName + " is disabled in config.yml");
            return;
        }

        queueManager = new QueueManager(server, logger, configManager, pluginInstance);
        joinListener = new PlayerJoinListener(server, logger, queueManager, configManager);

        server.getEventManager().register(pluginInstance, joinListener);
        server.getEventManager().register(pluginInstance, queueManager.getAddons());

        CommandManager commandManager = server.getCommandManager();

        CommandMeta queueMeta = commandManager.metaBuilder("queue").plugin(pluginInstance).build();
        commandManager.register(queueMeta, new QueueCommand(queueManager, configManager));

        CommandMeta leaveQueueMeta = commandManager.metaBuilder("leavequeue").plugin(pluginInstance).build();
        commandManager.register(leaveQueueMeta, new LeaveQueueCommand(queueManager, configManager));

        CommandMeta sevoqueueMeta = commandManager.metaBuilder("sevoqueue").aliases("svq").plugin(pluginInstance).build();
        commandManager.register(sevoqueueMeta, new SevoQueueReloadCommand());

        CommandMeta sendMeta = commandManager.metaBuilder("send").plugin(pluginInstance).build();
        commandManager.register(sendMeta, new SendCommand());

        if (configManager.isVelocityServerCommand()) {
            try { commandManager.unregister("server"); } catch (Exception ignored) { }
            CommandMeta serverMeta = commandManager.metaBuilder("server").plugin(pluginInstance).build();
            commandManager.register(serverMeta, new ServerCommand());
        }

        if (configManager.isSlashServer()) {
            registerSlashServerCommands();
            logger.info("SlashServer commands registered!");
        }

        if (licence.isLicenseValid()) {
            logger.info("=========================================");
            logger.info("SevoQueuePlus License Activated!");
            logger.info("Full features are now available");
            logger.info("=========================================");
        }

        logger.info("=========================================");
        logger.info(pluginDisplayName + " v1.0.8 Enabled!");
        logger.info("Queue time: {} seconds", configManager.getQueueSec());
        logger.info("Slash Server: {}", configManager.isSlashServer() ? "Enabled" : "Disabled");
        logger.info("=========================================");
    }

    public boolean hasFullFeatures() { return licence != null && licence.isLicenseValid(); }
    public String getPluginDisplayName() { return pluginDisplayName; }

    // ==================== LeaveQueue Command ====================
    private class LeaveQueueCommand implements SimpleCommand {
        private final QueueManager queueManager;
        private final ConfigManager configManager;

        public LeaveQueueCommand(QueueManager queueManager, ConfigManager configManager) {
            this.queueManager = queueManager;
            this.configManager = configManager;
        }

        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            if (!(source instanceof Player)) {
                source.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
                return;
            }
            Player player = (Player) source;
            String[] args = invocation.arguments();

            Set<String> queues = queueManager.getPlayerQueues(player);
            if (queues.isEmpty()) {
                player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(configManager.getNotInQueueMsg()));
                return;
            }

            if (args.length == 0) {
                if (queues.size() > 1) {
                    player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(configManager.getMultipleQueuesLeaveMsg()));
                    return;
                }
                String serverName = queues.iterator().next();
                queueManager.removeFromQueue(player, serverName, true);
            } else {
                String serverName = args[0];
                queueManager.removeFromQueue(player, serverName, true);
            }
        }

        @Override
        public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
            String[] args = invocation.arguments();
            List<String> suggestions = new ArrayList<>();
            if (!configManager.hasPermission(invocation.source(), "tabcomplete") || !(invocation.source() instanceof Player)) {
                return CompletableFuture.completedFuture(suggestions);
            }
            Player player = (Player) invocation.source();
            if (args.length == 1) {
                for (String q : queueManager.getPlayerQueues(player)) {
                    if (q.toLowerCase().startsWith(args[0].toLowerCase())) {
                        suggestions.add(q);
                    }
                }
            }
            return CompletableFuture.completedFuture(suggestions);
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

                if (configManager.isLicenseEnabled()) {
                    boolean valid = licence.checkLicense(configManager.getLicenseCode());
                    pluginDisplayName = valid ? "SevoQueuePlus" : "SevoQueue";
                } else {
                    pluginDisplayName = "SevoQueue";
                }

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
            if (configManager.hasPermission(invocation.source(), "tabcomplete")) {
                if (args.length == 1 && "reload".startsWith(args[0].toLowerCase())) {
                    suggestions.add("reload");
                }
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
                for (Player p : allPlayers) {
                    boolean alreadyThere = p.getCurrentServer()
                            .map(connection -> connection.getServerInfo().getName().equalsIgnoreCase(targetServer.get().getServerInfo().getName()))
                            .orElse(false);
                    if (alreadyThere) continue;
                    queueManager.addToQueue(p, serverName);
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

            Player p = targetPlayer.get();
            queueManager.addToQueue(p, serverName);
            source.sendMessage(Component.text("Sent " + playerName + " to " + serverName + " queue", NamedTextColor.GREEN));
            p.sendMessage(Component.text("You were sent to the queue for " + serverName + " by " + source, NamedTextColor.YELLOW));
        }

        @Override
        public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
            String[] args = invocation.arguments();
            List<String> suggestions = new ArrayList<>();

            if (!configManager.hasPermission(invocation.source(), "tabcomplete")) {
                return CompletableFuture.completedFuture(suggestions);
            }

            if (args.length == 1) {
                if ("all".startsWith(args[0].toLowerCase())) suggestions.add("all");
                for (Player p : server.getAllPlayers()) {
                    if (p.getUsername().toLowerCase().startsWith(args[0].toLowerCase())) {
                        suggestions.add(p.getUsername());
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

            if (!(source instanceof Player)) return;
            Player player = (Player) source;

            if (args.length == 0) {
                player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cUsage: /server <servername>"));
                return;
            }

            String serverName = args[0];
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
            try { commandManager.unregister(serverName); } catch (Exception ignored) { }

            CommandMeta commandMeta = commandManager.metaBuilder(serverName).plugin(pluginInstance).build();

            commandManager.register(commandMeta, new RawCommand() {
                @Override
                public void execute(RawCommand.Invocation invocation) {
                    if (!(invocation.source() instanceof Player)) {
                        invocation.source().sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
                        return;
                    }
                    Player player = (Player) invocation.source();
                    queueManager.addToQueue(player, serverName);
                }
            });

            logger.info("Registered slash command: /{}", serverName);
        }
    }
}