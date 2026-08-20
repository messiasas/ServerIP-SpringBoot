package com.transire.cloud6.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TrafficEvent {
    private String type;
    private String ip;
    private Integer port;
    private String family;
    private String timestamp;
    private Integer totalConnections;
    private Integer activeConnections;
    private String text;
    private String hex;
    private Integer bytes;
    private String message;
    private String name;
    private Integer replyPort;

    public static TrafficEvent of(String type) {
        TrafficEvent e = new TrafficEvent();
        e.type = type;
        e.timestamp = Instant.now().toString();
        return e;
    }

    public TrafficEvent ip(String v) { this.ip = v; return this; }
    public TrafficEvent port(Integer v) { this.port = v; return this; }
    public TrafficEvent family(String v) { this.family = v; return this; }
    public TrafficEvent totalConnections(Integer v) { this.totalConnections = v; return this; }
    public TrafficEvent activeConnections(Integer v) { this.activeConnections = v; return this; }
    public TrafficEvent text(String v) { this.text = v; return this; }
    public TrafficEvent hex(String v) { this.hex = v; return this; }
    public TrafficEvent bytes(Integer v) { this.bytes = v; return this; }
    public TrafficEvent message(String v) { this.message = v; return this; }
    public TrafficEvent name(String v) { this.name = v; return this; }
    public TrafficEvent replyPort(Integer v) { this.replyPort = v; return this; }

    public String getType() { return type; }
    public String getIp() { return ip; }
    public Integer getPort() { return port; }
    public String getFamily() { return family; }
    public String getTimestamp() { return timestamp; }
    public Integer getTotalConnections() { return totalConnections; }
    public Integer getActiveConnections() { return activeConnections; }
    public String getText() { return text; }
    public String getHex() { return hex; }
    public Integer getBytes() { return bytes; }
    public String getMessage() { return message; }
    public String getName() { return name; }
    public Integer getReplyPort() { return replyPort; }
}
