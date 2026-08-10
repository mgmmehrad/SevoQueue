package org.mgmmehrad.sevoqueue;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.slf4j.Logger;

import java.util.Optional;

public class PlayerJoinListener {
    private final ProxyServer server;
    private final Logger logger;
    private final QueueManager queueManager;
    private final ConfigManager configManager;
    private final Addons addons;

    public PlayerJoinListener(ProxyServer server, Logger logger, QueueManager queueManager, ConfigManager configManager) {
        this.server = server;
        this.logger = logger;
        this.queueManager = queueManager;
        this.configManager = configManager;
        // گرفتن اینستنس Addons از خود queueManager به جای ساخت مجدد آن با شیء اشتباه
        this.addons = queueManager.getAddons();
    }

    @Subscribe
    public void onPlayerChooseInitialServer(PlayerChooseInitialServerEvent event) {
        Player player = event.getPlayer();
        String defaultServer = configManager.getDefaultServer();

        server.getServer(defaultServer).ifPresentOrElse(
                s -> {
                    event.setInitialServer(s);
                    logger.info("Player {} assigned to default initial server {}", player.getUsername(), defaultServer);
                },
                () -> logger.warn("Default server '{}' not found for player {}", defaultServer, player.getUsername())
        );
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        Optional<RegisteredServer> targetServerOpt = event.getResult().getServer();

        if (targetServerOpt.isEmpty()) {
            return;
        }

        RegisteredServer targetServer = targetServerOpt.get();
        String targetServerName = targetServer.getServerInfo().getName();

        // اگر اتصال از طریق خود QueueManager در حال انجام است، اجازه اتصال داده شود
        if (queueManager.isConnectingViaQueue(player)) {
            return;
        }

        // اگر سرور مقصد همان سرور فعلی بازیکن است، نیازی به پردازش نیست
        if (player.getCurrentServer().isPresent() &&
                player.getCurrentServer().get().getServerInfo().getName().equalsIgnoreCase(targetServerName)) {
            return;
        }

        // لغو اتصال مستقیم (مانند کلیک روی NPC یا دستورهای مستقیم Velocity)
        event.setResult(ServerPreConnectEvent.ServerResult.denied());

        // افزودن بازیکن به Queue سرور مربوطه
        queueManager.addToQueue(player, targetServerName);
    }

    @Subscribe
    public void onKickedFromServer(KickedFromServerEvent event) {
        Player player = event.getPlayer();

        // پاکسازی وضعیت Queue کاربر در صورت کیک شدن
        queueManager.removeFromAllQueues(player, false);
        queueManager.setConnectingViaQueue(player, false);

        Optional<Component> reasonOpt = event.getServerKickReason();

        if (reasonOpt.isPresent()) {
            Component reasonComponent = reasonOpt.get();
            String reasonStr = PlainTextComponentSerializer.plainText().serialize(reasonComponent);

            if (reasonStr.contains("multiplayer.disconnect.banned")) {
                reasonStr = "You are banned from this server";
            } else if (reasonStr.contains("multiplayer.disconnect.not_whitelisted")) {
                reasonStr = "You are not whitelisted on this server";
            }

            String customTemplate = configManager.getServerCantConnectOthersMessage();
            String formattedMsg = customTemplate.replace("{1}", reasonStr);

            Component notifyComponent = formatMessage(formattedMsg);

            if (player.getCurrentServer().isPresent()) {
                event.setResult(KickedFromServerEvent.Notify.create(notifyComponent));
            } else {
                event.setResult(KickedFromServerEvent.DisconnectPlayer.create(notifyComponent));
            }
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        queueManager.removeFromAllQueues(player, false);
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