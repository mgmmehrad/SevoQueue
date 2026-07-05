package org.mgmmehrad.sevoqueue.bungee;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.api.plugin.Plugin;

import java.io.File;
import java.io.IOException;
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
        Map<String, String> nodes = new HashMap<>();
        nodes.put("join", "sevoqueue.join");
        nodes.put("leave", "sevoqueue.leave");
        nodes.put("status", "sevoqueue.status");
        nodes.put("reload", "sevoqueue.reload");
        nodes.put("tabcomplete", "sevoqueue.tabcomplete");
        nodes.put("send", "sevoqueue.send");
        nodes.put("server", "sevoqueue.server");
        nodes.put("slashserver", "sevoqueue.slashserver");
        return nodes;
    }

    public void loadConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        File file = new File(plugin.getDataFolder(), "config.yml");

        if (!file.exists()) {
            try (InputStream in = plugin.getResourceAsStream("config.yml")) {
                if (in != null) {
                    Files.copy(in, file.toPath());
                } else {
                    file.createNewFile();
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create config.yml: " + e.getMessage());
            }
        }

        try {
            config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not load config.yml: " + e.getMessage());
            return;
        }

        // هماهنگ‌سازی دقیق با حروف بزرگ و کوچک داخل config.yml شما
        enabled = config.getBoolean("Enabled", config.getBoolean("enabled", true));

        // اصلاح خواندن دقیق بخش queue-sec بدون حساسیت شدید به حروف بزرگ و کوچک
        if (config.contains("queue-sec")) {
            queueSec = config.getInt("queue-sec", 5);
        } else if (config.contains("Queue-Sec")) {
            queueSec = config.getInt("Queue-Sec", 5);
        } else {
            queueSec = 5;
        }

        queueMessage = config.getString("Queue-Message", config.getString("queue-message", "&aYou are in position &e{pos} &aYour remaining time is &e{time} &aseconds"));
        actionBarMessage = config.getString("ActionBar-Message", config.getString("actionbar-message", "&eQueue: &a{server} &7| &ePosition: &a{pos} &7| &eTime: &a{time}s"));
        slashServer = config.getBoolean("slash-server", true);
        licence = config.getBoolean("licence", false);
        licenceCode = config.getString("Licence-Code", config.getString("licence-code", ""));
        velocityServerCommand = config.getBoolean("velocity-server-command", true);
        defaultServer = config.getString("default-server", "lobby");

        // بارگذاری پرمیشن‌ها بر اساس ساختار کانفیگ شما
        Configuration permissionsSection = config.getSection("Permissions");
        if (permissionsSection == null) {
            permissionsSection = config.getSection("permissions");
        }

        if (permissionsSection != null) {
            for (String key : permissionsSection.getKeys()) {
                String node = permissionsSection.getString(key + ".permission");
                if (node == null) {
                    node = permissionsSection.getString(key + ".node");
                }
                boolean isEnabled = permissionsSection.getBoolean(key + ".enabled", true);
                if (node != null) {
                    permissionNodes.put(key.toLowerCase(), node);
                }
                permissionEnabled.put(key.toLowerCase(), isEnabled);
            }
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
        return permissionNodes.getOrDefault(key.toLowerCase(), DEFAULT_PERMISSION_NODES.getOrDefault(key.toLowerCase(), "sevoqueue." + key.toLowerCase()));
    }

    public boolean isPermissionEnabled(String key) {
        return permissionEnabled.getOrDefault(key.toLowerCase(), true);
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

    public void setLicenseValid(boolean valid) {
        this.licenseValid = valid;
        if (valid) {
            this.pluginDisplayName = "SevoQueuePlus";
        } else {
            this.pluginDisplayName = "SevoQueue";
        }
    }
}