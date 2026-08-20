package com.transire.cloud6.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.transire.cloud6.device.Device;
import com.transire.cloud6.device.DevicePingService;
import com.transire.cloud6.device.DeviceRegistry;
import com.transire.cloud6.event.DevicesSnapshotMessage;
import com.transire.cloud6.event.EventBus;
import com.transire.cloud6.event.TrafficEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executors;

@Component
public class DeviceApiServer {

    @Value("${api.port:7777}")
    private int port;

    private final EventBus eventBus;
    private final DeviceRegistry deviceRegistry;
    private final DevicePingService devicePingService;
    private final ObjectMapper mapper = new ObjectMapper();

    private HttpServer server;

    public DeviceApiServer(EventBus eventBus, DeviceRegistry deviceRegistry,
                            DevicePingService devicePingService) {
        this.eventBus = eventBus;
        this.deviceRegistry = deviceRegistry;
        this.devicePingService = devicePingService;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/devices/ping", this::handleDevicePing);
        server.createContext("/api/ping", this::handleApiPing);
        server.createContext("/api/devices", this::handleListDevices);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("[API] API de dispositivos (IPv4+IPv6) ativa na porta " + port);
    }

    private String familyOf(HttpExchange exchange) {
        InetAddress addr = exchange.getRemoteAddress().getAddress();
        return (addr instanceof Inet4Address) ? "IPv4" : "IPv6";
    }

    private String ipOf(HttpExchange exchange) {
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    private void handleDevicePing(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("message", "metodo nao permitido"));
            return;
        }

        Map<?, ?> body = mapper.readValue(exchange.getRequestBody(), Map.class);
        String name = (String) body.get("name");
        Object replyPortObj = body.get("replyPort");

        if (name == null || replyPortObj == null) {
            sendJson(exchange, 400, Map.of("message", "name e replyPort sao obrigatorios"));
            return;
        }

        int replyPort = ((Number) replyPortObj).intValue();
        String ip = ipOf(exchange);
        String family = familyOf(exchange);
        String now = Instant.now().toString();

        Device existing = deviceRegistry.get(name);
        Device device = new Device();
        device.setName(name);
        device.setIp(ip);
        device.setReplyPort(replyPort);
        device.setFamily(family);
        device.setRegisteredAt(existing != null ? existing.getRegisteredAt() : now);
        device.setLastSeen(now);
        if (existing != null) {
            device.setLastPingBackAt(existing.getLastPingBackAt());
            device.setLastPingBackStatus(existing.getLastPingBackStatus());
            device.setLastPingBackMessage(existing.getLastPingBackMessage());
        }
        deviceRegistry.put(device);

        eventBus.pushEvent(
            TrafficEvent.of("device_register").ip(ip).name(name).replyPort(replyPort).family(family)
        );

        DevicesSnapshotMessage snapshot = new DevicesSnapshotMessage();
        snapshot.devices = deviceRegistry.snapshot();
        eventBus.broadcast(snapshot);

        sendJson(exchange, 200, Map.of(
            "message", "dispositivo registrado",
            "remoteAddress", ip,
            "family", family,
            "timestamp", System.currentTimeMillis()
        ));

        devicePingService.pingBackAndReport(device);
    }

    private void handleApiPing(HttpExchange exchange) throws IOException {
        String ip = ipOf(exchange);
        String family = familyOf(exchange);

        eventBus.pushEvent(TrafficEvent.of("api_ping").ip(ip).family(family));

        sendJson(exchange, 200, Map.of(
            "message", "pong",
            "remoteAddress", ip,
            "family", family,
            "timestamp", System.currentTimeMillis()
        ));
    }

    private void handleListDevices(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, deviceRegistry.snapshot());
    }

    private void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public void stop() {
        if (server != null) server.stop(0);
    }
}
