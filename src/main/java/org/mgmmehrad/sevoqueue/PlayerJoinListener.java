package org.mgmmehrad.sevoqueue;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import java.util.Optional;

public class PlayerJoinListener {
    private final ProxyServer server;
    private final Logger logger;
    private final QueueManager queueManager;
    private final ConfigManager configManager;

    public PlayerJoinListener(ProxyServer server, Logger logger, QueueManager queueManager, ConfigManager configManager) {
        this.server = server;
        this.logger = logger;
        this.queueManager = queueManager;
        this.configManager = configManager;
    }

    @Subscribe
    public void onPlayerChooseInitialServer(PlayerChooseInitialServerEvent event) {
        Player player = event.getPlayer();
        String defaultServer = configManager.getDefaultServer();

        server.getServer(defaultServer).ifPresentOrElse(
                s -> {
                    event.setInitialServer(s);
                    sendMessage(player, "&aWelcome! You have been connected to &e" + defaultServer);
                },
                () -> {
                    logger.warn("Default server {} not found!", defaultServer);
                    event.setInitialServer(null);
                }
        );
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        String targetServer = event.getOriginalServer().getServerInfo().getName();

        // 1. اگر بازیکن توسط خود سیستم صف در حال متصل شدن است، اجازه اتصال بده
        if (queueManager.isConnectingViaQueue(player)) {
            queueManager.setConnectingViaQueue(player, false);
            return;
        }

        // 2. اگر بازیکن از قبل داخل یک صف حضور دارد، اتصال مستقیم جدید را مسدود کن
        if (queueManager.isInQueue(player)) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            sendMessage(player, "&cYou are in queue! Use &e/queue leave &cto cancel.");
            return;
        }

        // 3. مدیریت بازیکنانی که پرمیشن بای‌پاس دارند یا اولین ورودشان به سرور (پراکسی) است
        boolean isInitialConnection = player.getCurrentServer().isEmpty();
        boolean canBypass = queueManager.canBypassQueue(player);

        if (isInitialConnection || canBypass) {
            if (!queueManager.getAddons().canConnectToServer(targetServer)) {
                String statusMsg = queueManager.getAddons().getServerStatusMessage(targetServer);
                if (statusMsg != null) {
                    event.setResult(ServerPreConnectEvent.ServerResult.denied());
                    sendMessage(player, statusMsg);
                    return;
                }
            }
            if (canBypass) {
                queueManager.removeFromQueue(player, false);
            }
            return;
        }

        // 4. برای انتقال‌های معمولی و کلیک روی NPC های Znpcs
        if (configManager.isVelocityServerCommand()) {
            // اتصال مستقیم اولیه را مسدود کن و بازیکن را به سیستم صف بفرست (چه سرور آنلاین باشد چه آفلاین/Starting)
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            queueManager.addToQueue(player, targetServer);
        } else {
            // اگر قابلیت فورس کردن صف غیرفعال است، فقط در صورت آفلاین بودن سرور ارور معمولی بده
            if (!queueManager.getAddons().canConnectToServer(targetServer)) {
                String statusMsg = queueManager.getAddons().getServerStatusMessage(targetServer);
                if (statusMsg != null) {
                    event.setResult(ServerPreConnectEvent.ServerResult.denied());
                    sendMessage(player, statusMsg);
                }
            }
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        queueManager.removeFromQueue(player, false);
        queueManager.setConnectingViaQueue(player, false);
    }

    private void sendMessage(Player player, String message) {
        player.sendMessage(formatMessage(message));
    }

    private Component formatMessage(String message) {
        String normalizedMessage = message == null ? "" : message.replace((char) 167, '&');
        return LegacyComponentSerializer.legacyAmpersand().deserialize(normalizedMessage);
    }
}