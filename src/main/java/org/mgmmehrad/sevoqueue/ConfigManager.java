package org.mgmmehrad.sevoqueue;

import com.velocitypowered.api.command.CommandSource;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.InputStream;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConfigManager {
    private final Path dataDirectory;
    private final Logger logger;
    private CommentedConfigurationNode config;

    private boolean enabled;
    private int queueSec;
    private String queueMessage;
    private String actionBarMessage;
    private String serverCantConnectMessage;
    private String serverCantConnectActionBar;
    private String serverCantConnectOthersMessage;
    private boolean slashServer;
    private boolean licence;
    private String licenceCode;
    private boolean velocityServerCommand;
    private String defaultServer;

    // متغیرهای پیام‌های جدید
    private String multipleQueuesLeaveMsg;
    private String alreadyInQueueMsg;
    private String leftQueueMsg;
    private String notInQueueMsg;
    private String notInSpecificQueueMsg;
    private String statusInQueuesMsg;
    private String queueLeaveUsageMsg;

    private final Map<String, String> permissionNodes = new HashMap<>();
    private final Map<String, Boolean> permissionEnabled = new HashMap<>();
    private static final Map<String, String> DEFAULT_PERMISSION_NODES = createDefaultPermissionNodes();

    public ConfigManager(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    private static Map<String, String> createDefaultPermissionNodes() {
        Map<String, String> permissions = new LinkedHashMap<>();
        permissions.put("bypass", "sevoqueue.bypass");
        permissions.put("join", "sevoqueue.join");
        permissions.put("leave", "sevoqueue.leave");
        permissions.put("status", "sevoqueue.status");
        permissions.put("reload", "sevoqueue.reload");
        permissions.put("send", "sevoqueue.send");
        permissions.put("server", "sevoqueue.server");
        permissions.put("slashserver", "sevoqueue.slashserver");
        permissions.put("tabcomplete", "sevoqueue.tabcomplete");
        return Collections.unmodifiableMap(permissions);
    }

    public void loadConfig() {
        try {
            Path configPath = dataDirectory.resolve("config.yml");

            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }

            if (!Files.exists(configPath)) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                    if (in != null) {
                        Files.copy(in, configPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(configPath)
                    .build();

            config = loader.load();

            enabled = config.node("Enabled").getBoolean(true);
            queueSec = Math.max(0, config.node("queue-sec").getInt(5));
            queueMessage = config.node("Queue-Message").getString("&aYou are in position &e{pos} &aYour remaining time is &e{time} &aseconds");
            actionBarMessage = config.node("ActionBar-Message").getString("&eQueue: &a{server} &7| &ePosition: &a{pos} &7| &eTime: &a{time}s");
            serverCantConnectMessage = config.node("Server-Cant-Connect-Message").getString("&cServer Is {Reason}&7. &aYou are in position &e{pos}");
            serverCantConnectActionBar = config.node("Server-Cant-Connect-ActionBar").getString("&cServer Is {Reason}&7. &aYou are in position &e{pos}");
            serverCantConnectOthersMessage = config.node("Server-cant-connect-others-message").getString("{1}");

            slashServer = config.node("slash-server").getBoolean(true);
            licence = config.node("licence").getBoolean(false);
            licenceCode = config.node("Licence-Code").getString("");
            velocityServerCommand = config.node("velocity-server-command").getBoolean(true);
            defaultServer = config.node("default-server").getString("lobby");

            // تولید خودکار بخش Messages در صورت عدم وجود
            boolean changed = false;
            CommentedConfigurationNode messagesNode = config.node("messages");
            if (messagesNode.virtual()) {
                messagesNode.node("multiple-queues-leave").raw("&cYou are in multiple queues! Type &e/leavequeue <server> &cfor example.");
                messagesNode.node("already-in-queue").raw("&cYou are already in the queue for &e{server}!");
                messagesNode.node("left-queue").raw("&aYou left the queue for &e{server}!");
                messagesNode.node("not-in-queue").raw("&cYou are not in any queue!");
                messagesNode.node("not-in-specific-queue").raw("&cYou are not in the queue for &e{server}!");
                messagesNode.node("status-in-queues").raw("&aYou are in queues for: &e{servers}");
                messagesNode.node("queue-leave-usage").raw("&cUsage: /queue leave <server>");
                changed = true;
            }

            multipleQueuesLeaveMsg = messagesNode.node("multiple-queues-leave").getString("&cYou are in multiple queues! Type &e/leavequeue <server> &cfor example.");
            alreadyInQueueMsg = messagesNode.node("already-in-queue").getString("&cYou are already in the queue for &e{server}!");
            leftQueueMsg = messagesNode.node("left-queue").getString("&aYou left the queue for &e{server}!");
            notInQueueMsg = messagesNode.node("not-in-queue").getString("&cYou are not in any queue!");
            notInSpecificQueueMsg = messagesNode.node("not-in-specific-queue").getString("&cYou are not in the queue for &e{server}!");
            statusInQueuesMsg = messagesNode.node("status-in-queues").getString("&aYou are in queues for: &e{servers}");
            queueLeaveUsageMsg = messagesNode.node("queue-leave-usage").getString("&cUsage: /queue leave <server>");

            loadPermissions(loader, changed);

            logger.info("Config loaded successfully!");

        } catch (ConfigurateException e) {
            logger.error("Failed to load config!", e);
        } catch (Exception e) {
            logger.error("Unexpected error loading config!", e);
        }
    }

    public boolean isLicenseEnabled() { return licence; }
    public String getLicenseCode() { return licenceCode; }

    private void loadPermissions(YamlConfigurationLoader loader, boolean configChanged) throws Exception {
        boolean changed = configChanged;
        permissionNodes.clear();
        permissionEnabled.clear();

        for (Map.Entry<String, String> entry : DEFAULT_PERMISSION_NODES.entrySet()) {
            String key = entry.getKey();
            String defaultPermission = entry.getValue();

            CommentedConfigurationNode permissionsSection = getPermissionsSection();
            CommentedConfigurationNode permissionNode = permissionsSection.node(key, "permission");
            if (permissionNode.virtual()) {
                permissionNode.raw(defaultPermission);
                changed = true;
            }

            CommentedConfigurationNode enabledNode = permissionsSection.node(key, "enabled");
            if (enabledNode.virtual()) {
                enabledNode.raw(true);
                changed = true;
            }

            permissionNodes.put(key, permissionNode.getString(defaultPermission));
            permissionEnabled.put(key, enabledNode.getBoolean(true));
        }

        if (changed) {
            loader.save(config);
        }

        logger.info("Permissions loaded. Bypass: {} ({})",
                getPermission("bypass"),
                isPermissionEnabled("bypass") ? "enabled" : "disabled");
    }

    private CommentedConfigurationNode getPermissionsSection() {
        CommentedConfigurationNode permissionsSection = config.node("Permissions");
        if (!permissionsSection.virtual()) {
            return permissionsSection;
        }
        CommentedConfigurationNode lowercasePermissionsSection = config.node("permissions");
        if (!lowercasePermissionsSection.virtual()) {
            return lowercasePermissionsSection;
        }
        return permissionsSection;
    }

    public boolean isEnabled() { return enabled; }
    public int getQueueSec() { return queueSec; }
    public String getQueueMessage() { return queueMessage; }
    public String getActionBarMessage() { return actionBarMessage; }
    public String getServerCantConnectMessage() { return serverCantConnectMessage; }
    public String getServerCantConnectActionBar() { return serverCantConnectActionBar; }
    public String getServerCantConnectOthersMessage() { return serverCantConnectOthersMessage; }
    public boolean isSlashServer() { return slashServer; }
    public boolean isVelocityServerCommand() { return velocityServerCommand; }
    public String getDefaultServer() { return defaultServer; }

    // Getters برای پیام‌های جدید
    public String getMultipleQueuesLeaveMsg() { return multipleQueuesLeaveMsg; }
    public String getAlreadyInQueueMsg() { return alreadyInQueueMsg; }
    public String getLeftQueueMsg() { return leftQueueMsg; }
    public String getNotInQueueMsg() { return notInQueueMsg; }
    public String getNotInSpecificQueueMsg() { return notInSpecificQueueMsg; }
    public String getStatusInQueuesMsg() { return statusInQueuesMsg; }
    public String getQueueLeaveUsageMsg() { return queueLeaveUsageMsg; }

    public String getPermission(String key) {
        return permissionNodes.getOrDefault(key, DEFAULT_PERMISSION_NODES.getOrDefault(key, "sevoqueue." + key));
    }

    public boolean isPermissionEnabled(String key) {
        return permissionEnabled.getOrDefault(key, true);
    }

    public boolean hasPermission(CommandSource source, String key) {
        return !isPermissionEnabled(key) || source.hasPermission(getPermission(key));
    }

    public boolean hasEnabledPermission(CommandSource source, String key) {
        return isPermissionEnabled(key) && source.hasPermission(getPermission(key));
    }

    public void reloadConfig() {
        loadConfig();
    }
}