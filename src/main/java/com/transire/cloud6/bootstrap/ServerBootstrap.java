package com.transire.cloud6.bootstrap;

import com.transire.cloud6.api.DeviceApiServer;
import com.transire.cloud6.echo.EchoServer;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ServerBootstrap {

    private final EchoServer echoServer;
    private final DeviceApiServer deviceApiServer;

    public ServerBootstrap(EchoServer echoServer, DeviceApiServer deviceApiServer) {
        this.echoServer = echoServer;
        this.deviceApiServer = deviceApiServer;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() throws Exception {
        echoServer.start();
        deviceApiServer.start();
    }
}
