package com.transire.cloud6.device;

import com.transire.cloud6.event.DevicesSnapshotMessage;
import com.transire.cloud6.event.EventBus;
import com.transire.cloud6.event.TrafficEvent;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class DevicePingService {

    private static final int PINGBACK_TIMEOUT_MS = 5000;

    private final EventBus eventBus;
    private final DeviceRegistry deviceRegistry;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(PINGBACK_TIMEOUT_MS))
        .build();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public DevicePingService(EventBus eventBus, DeviceRegistry deviceRegistry) {
        this.eventBus = eventBus;
        this.deviceRegistry = deviceRegistry;
    }

    public void pingBackAndReport(Device device) {
        executor.submit(() -> doPingBack(device));
    }

    private void doPingBack(Device device) {
        String hostForUrl = device.getIp().contains(":") ? "[" + device.getIp() + "]" : device.getIp();
        String url = "http://" + hostForUrl + ":" + device.getReplyPort() + "/api/ping";

        boolean ok;
        String resultMessage;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(PINGBACK_TIMEOUT_MS))
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ok = response.statusCode() >= 200 && response.statusCode() < 300;
            resultMessage = ok ? response.body() : "HTTP " + response.statusCode();
        } catch (Exception e) {
            ok = false;
            resultMessage = e.getMessage();
        }

        device.setLastPingBackAt(Instant.now().toString());
        device.setLastPingBackStatus(ok ? "ok" : "falha");
        device.setLastPingBackMessage(resultMessage);
        deviceRegistry.put(device);

        eventBus.pushEvent(
            TrafficEvent.of(ok ? "pingback_ok" : "pingback_fail")
                .ip(device.getIp())
                .name(device.getName())
                .replyPort(device.getReplyPort())
                .family(device.getFamily())
                .message(resultMessage)
        );

        DevicesSnapshotMessage snapshot = new DevicesSnapshotMessage();
        snapshot.devices = deviceRegistry.snapshot();
        eventBus.broadcast(snapshot);
    }
}
