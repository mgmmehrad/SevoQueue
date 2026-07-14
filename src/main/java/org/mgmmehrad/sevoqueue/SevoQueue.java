package org.mgmmehrad.sevoqueue;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;
import org.bstats.velocity.Metrics;
import java.nio.file.Path;

@Plugin(id = "sevoqueue", name = "SevoQueue", version = "1.0.5", authors = {"mgmmehrad"})
public class SevoQueue {
    private final Main pluginMain;

    @Inject
    public SevoQueue(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory, Metrics.Factory metricsFactory) {
        // پاس دادن نمونه همین کلاس (this) به عنوان اینستنس پلاگین به Main
        this.pluginMain = new Main(this, server, logger, dataDirectory, metricsFactory);
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        this.pluginMain.initialize(event);
    }
}