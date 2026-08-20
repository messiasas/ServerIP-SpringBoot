package com.transire.serverip.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transire.serverip.device.DevicePingService;
import com.transire.serverip.device.DeviceRegistry;
import com.transire.serverip.event.DeviceSnapshotMessage;
import com.transire.serverip.event.EventBus;
import com.transire.serverip.event.SnapshotMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;


@Component
public class TrafficWebSocketHandler extends TextWebSocketHandler {

    private final EventBus eventBus;
    private final DeviceRegistry deviceRegistry;
    private final DevicePingService devicePingService;
    private final ObjectMapper mapper = new ObjectMapper();

    public TrafficWebSocketHandler(EventBus eventBus, DeviceRegistry deviceRegistry,
                                   DevicePingService devicePingService) {
        this.eventBus = eventBus;
        this.deviceRegistry = deviceRegistry;
        this.devicePingService = devicePingService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        eventBus.registerSession(session);

        SnapshotMessage snapshot = new SnapshotMessage();
        snapshot.totConnections = eventBus.getTotalConnections();
        snapshot.activeConnections = eventBus.getActiveConnections();
        snapshot.history = eventBus.historySnapshot();
        session.sendMessage(new TextMessage(mapper.writeValueAsString(snapshot)));

        DeviceSnapshotMessage devicesSnapshot = new DeviceSnapshotMessage();
        devicesSnapshot.devices = deviceRegistry.snapshot();
        session.sendMessage(new TextMessage(mapper.writeValueAsString(devicesSnapshot)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        eventBus.unregisterSession(session);
    }

    // Trata a mensagem { type: "ping_device", name: "..." } que o botão
    // "Testar ping" do painel manda.
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<?, ?> payload = mapper.readValue(message.getPayload(), Map.class);
        if ("ping_device".equals(payload.get("type"))) {
            Object nameObj = payload.get("name");
            if (nameObj instanceof String name) {
                var device = deviceRegistry.get(name);
                if (device != null) devicePingService.pingBackAndReport(device);
            }
        }
    }
}
