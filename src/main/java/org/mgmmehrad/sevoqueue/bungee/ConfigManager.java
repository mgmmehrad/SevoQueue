package org.mgmmehrad.sevoqueue.bungee;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.api.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;

public class ConfigManager {
    private final Plugin plugin;
    private Configuration config;

    private boolean enabled;
    private int queueSec;
    private String queueMessage;
    private String actionBarMessage;
    private boolean slashServer;
    private boolean licence;
    private String licenceCode;
    private boolean velocityServerCommand;
    private String defaultServer;
    private final Map<String, String> permissionNodes = new HashMap<>();
    private final Map<String, Boolean> permissionEnabled = new HashMap<>();
    private static final Map<String, String> DEFAULT_PERMISSION_NODES = createDefaultPermissionNodes();

    private boolean licenseValid = false;
    private String pluginDisplayName = "SevoQueue";

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
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
            File configFile = new File(plugin.getDataFolder(), "config.yml");

            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            if (!configFile.exists()) {
                try (InputStream in = plugin.getResourceAsStream("config.yml")) {
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
            slashServer = config.getBoolean("slash-server", true);
            licence = config.getBoolean("licence", false);
            licenceCode = config.getString("Licence-Code", "");
            velocityServerCommand = config.getBoolean("velocity-server-command", true);
            defaultServer = config.getString("default-server", "lobby");
            loadPermissions();

            checkLicenseAndUpdateName();

            plugin.getLogger().info("Config loaded successfully!");

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load config!");
            e.printStackTrace();
        }
    }

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
            plugin.getLogger().info("✓ License validated! Running SevoQueuePlus with full features.");
        } else if (licence) {
            licenseValid = false;
            pluginDisplayName = "SevoQueue";
            plugin.getLogger().warning("✗ Invalid license code! Please check your Licence-Code in config.yml");
        } else {
            licenseValid = false;
            pluginDisplayName = "SevoQueue";
            plugin.getLogger().info("License feature is disabled. Running standard SevoQueue.");
        }
    }

    private void loadPermissions() {
        permissionNodes.clear();
        permissionEnabled.clear();

        for (Map.Entry<String, String> entry : DEFAULT_PERMISSION_NODES.entrySet()) {
            String key = entry.getKey();
            String defaultPermission = entry.getValue();

            String permission = config.getString("Permissions." + key + ".permission", defaultPermission);
            boolean enabled = config.getBoolean("Permissions." + key + ".enabled", true);

            permissionNodes.put(key, permission);
            permissionEnabled.put(key, enabled);
        }
    }

    public void reloadConfig() {
        loadConfig();
    }

    public boolean isEnabled() { return enabled; }
    public int getQueueSec() { return queueSec; }
    public String getQueueMessage() { return queueMessage; }
    public String getActionBarMessage() { return actionBarMessage; }
    public boolean isSlashServer() { return slashServer; }
    public boolean isLicenseEnabled() { return licence; }
    public String getLicenseCode() { return licenceCode; }
    public boolean isVelocityServerCommand() { return velocityServerCommand; }
    public String getDefaultServer() { return defaultServer; }

    public String getPermission(String key) {
        return permissionNodes.getOrDefault(key, DEFAULT_PERMISSION_NODES.getOrDefault(key, "sevoqueue." + key));
    }

    public boolean isPermissionEnabled(String key) {
        return permissionEnabled.getOrDefault(key, true);
    }

    public boolean hasPermission(CommandSender sender, String key) {
        if (!isPermissionEnabled(key)) return true;
        if (sender instanceof ProxiedPlayer) {
            return sender.hasPermission(getPermission(key));
        }
        return true;
    }

    public boolean hasEnabledPermission(CommandSender sender, String key) {
        return isPermissionEnabled(key) && sender.hasPermission(getPermission(key));
    }

    public boolean hasFullFeatures() {
        return licenseValid;
    }

    public String getPluginDisplayName() {
        return pluginDisplayName;
    }
}