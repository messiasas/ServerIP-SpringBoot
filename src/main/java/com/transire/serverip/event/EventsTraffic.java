package com.transire.serverip.event;

import com.fasterxml.jackson.annotation.JsonInclude; // Responsible lib that convert objects to JSON and contrary
import java.time.Instant; // take exactly requisition time


@JsonInclude(JsonInclude.Include.NON_NULL) // Omit null spaces on final JSON
// Essa lib acima faz com que campos vazios não entre na string, consequentemente na interface
public class EventsTraffic {

    private String type;
    private String ip;
    private Integer port;
    private String family;
    private String timestamp;
    private Integer totConnections;
    private Integer activeConnections;
    private String text;
    private String hex;
    private Integer bytes;
    private String message;
    private String name;
    private Integer replyPort;

    // Use 'of' because, if we want to extend the project in the future — for example, by creating
    // another constructor like `construction(String errorReport)` — the IDE will prevent this,
    // since we cannot create two constructors with the same name.

    // But, if we creating EventsTraffic of(), we can create EventsTraffic ofError() and others constructions with same name

    public static EventsTraffic of(String type) {
        EventsTraffic e = new EventsTraffic();
        e.type = type;
        e.timestamp = Instant.now().toString();
        return e;
    }

    // Temos abaixo os fluent setter, que retornam this. Isso serve para nao precisar
    // chamar o objeto toda hora com atributo:
    /*
    * TrafficEvent evento = new TrafficEvent();
        evento.setBytes(1024);
        evento.setType("DATA_RECEIVED");
        evento.setIp("192.168.0.10");*/

    // Com fluent setters podemos encadear tudo:
    /*TrafficEvent evento = new TrafficEvent()
    .bytes(1024)
    .type("DATA_RECEIVED")
    .ip("192.168.0.10");*/

    public EventsTraffic ip(String v) {
        this.ip = v;
        return this;
    }

    public EventsTraffic port(Integer v) {
        this.port = v;
        return this;
    }

    public EventsTraffic family(String v) {
        this.family = v;
        return this;
    }

    public EventsTraffic totConnections(Integer v) {
        this.totConnections = v;
        return this;
    }

    public EventsTraffic activeConnections(Integer v) {
        this.activeConnections = v;
        return this;
    }

    public EventsTraffic text(String v) {
        this.text = v;
        return this;
    }

    public EventsTraffic hex(String v) {
        this.hex = v;
        return this;
    }

    public EventsTraffic bytes(Integer v) {
        this.bytes = v;
        return this;
    }

    public EventsTraffic message(String v) {
        this.message = v;
        return this;
    }

    public EventsTraffic name(String v) {
        this.name = v;
        return this;
    }

    public EventsTraffic replyPort(Integer v) {
        this.replyPort = v;
        return this;
    }
}