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
    private boolean slashServer;
    private boolean licence;
    private String licenceCode;
    private boolean velocityServerCommand;
    private String defaultServer;
    private final Map<String, String> permissionNodes = new HashMap<>();
    private final Map<String, Boolean> permissionEnabled = new HashMap<>();
    private static final Map<String, String> DEFAULT_PERMISSION_NODES = createDefaultPermissionNodes();

    // License System
    private boolean licenseValid = false;
    private String pluginDisplayName = "SevoQueue";

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

            // New Config Options
            serverCantConnectMessage = config.node("Server-Cant-Connect-Message").getString("&cServer Is {Reason}&7. &aYou are in position &e{pos}");
            serverCantConnectActionBar = config.node("Server-Cant-Connect-ActionBar").getString("&cServer Is {Reason}&7. &aYou are in position &e{pos}");

            slashServer = config.node("slash-server").getBoolean(true);
            licence = config.node("licence").getBoolean(false);
            licenceCode = config.node("Licence-Code").getString("");
            velocityServerCommand = config.node("velocity-server-command").getBoolean(true);
            defaultServer = config.node("default-server").getString("lobby");
            loadPermissions(loader);

            // Check license after loading config
            checkLicenseAndUpdateName();

            logger.info("Config loaded successfully!");

        } catch (ConfigurateException e) {
            logger.error("Failed to load config!", e);
        } catch (Exception e) {
            logger.error("Unexpected error loading config!", e);
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
        if (licence && checkLicense(licenceCode)) {
            licenseValid = true;
            pluginDisplayName = "SevoQueuePlus";
            logger.info("✓ License validated! Running SevoQueuePlus with full features.");
        } else if (licence) {
            licenseValid = false;
            pluginDisplayName = "SevoQueue-Licence";
            logger.warn("✗ Invalid license code! Please check your Licence-Code in config.yml");
        } else {
            licenseValid = false;
            pluginDisplayName = "SevoQueue-Licence";
            logger.info("License not found.");
        }
    }

    public void sendLicenseMessage() {
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

    public boolean isLicenseValid() {
        return licenseValid;
    }

    public boolean isLicenseEnabled() {
        return licence;
    }

    public String getLicenseCode() {
        return licenceCode;
    }

    private void loadPermissions(YamlConfigurationLoader loader) throws Exception {
        boolean changed = false;
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

    // New Getters
    public String getServerCantConnectMessage() { return serverCantConnectMessage; }
    public String getServerCantConnectActionBar() { return serverCantConnectActionBar; }

    public boolean isSlashServer() { return slashServer; }
    public boolean hasLicence() { return licence; }
    public String getLicenceCode() { return licenceCode; }
    public boolean isVelocityServerCommand() { return velocityServerCommand; }
    public String getDefaultServer() { return defaultServer; }

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