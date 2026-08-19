# Guia de Migração — Cloud6 (ECHO-6) de Node.js para Java + Spring Boot

> Documento de referência completo, dividido em fases. Cada fase tem: objetivo,
> pré-requisitos, passo a passo com código, explicação do "por quê", e um
> checklist de validação antes de seguir pra próxima. Pode ser usado em
> qualquer chat/sessão — está tudo autocontido.

---

## Sumário

- [Fase 0 — Visão geral e decisões de arquitetura](#fase-0)
- [Fase 1 — Setup do projeto Spring Boot](#fase-1)
- [Fase 2 — Modelo de eventos e o EventBus](#fase-2)
- [Fase 3 — Servidor TCP Echo (porta 5000)](#fase-3)
- [Fase 4 — WebSocket do painel](#fase-4)
- [Fase 5 — Front-end (HTML/CSS/JS)](#fase-5)
- [Fase 6 — Registro e API de dispositivos (porta 7777)](#fase-6)
- [Fase 7 — Configuração (properties + variáveis de ambiente)](#fase-7)
- [Fase 8 — Orquestração de inicialização](#fase-8)
- [Fase 9 — Testes locais completos](#fase-9)
- [Fase 10 — Empacotamento (JAR executável)](#fase-10)
- [Fase 11 — Deploy no Azure](#fase-11)

---

<a name="fase-0"></a>
## Fase 0 — Visão geral e decisões de arquitetura

### O que o app original faz (recapitulando)

O **Cloud6 (ECHO-6)** é um servidor Node.js com três responsabilidades:

1. **Echo TCP** na porta `5000`, em IPv6 com fallback IPv4 — recebe bytes de
   uma conexão e devolve exatamente o mesmo conteúdo.
2. **API de dispositivos** na porta `7777` (dual-stack) — usada por um app
   Android que se registra (`POST /api/devices/ping`) e responde a um "ping"
   manual (`GET /api/ping`); o servidor tenta contatar de volta o celular
   (ping-pong) e reporta o resultado.
3. **Painel web** na porta `3000` (Express + WebSocket) — mostra em tempo real
   uma tabela de tráfego e os dispositivos registrados.

### Mapeamento Node → Java

| Peça do Node | Equivalente em Java |
|---|---|
| `net.createServer` (echo) | `java.net.ServerSocket` + pool de threads |
| `express` + `ws` (painel) | Spring Boot (`spring-boot-starter-web` + `spring-boot-starter-websocket`) |
| API de dispositivos (porta 7777) | `com.sun.net.httpserver.HttpServer` (já vem no JDK, sem lib extra) |
| `public/*.html/css/js` | `src/main/resources/static/` — copiado praticamente sem mudanças |
| Variáveis de ambiente | `application.properties` com placeholders `${VAR:default}` |
| `JSON.stringify` / `ws.send` | Jackson (`ObjectMapper`) + `WebSocketSession.sendMessage` |

### Decisão de arquitetura importante: uma porta só, dual-stack

No Node, o app original abre **dois sockets** na porta 5000 (um IPv6-only, um
IPv4) porque o Node, por padrão, não force o modo dual-stack a menos que você
peça — e o autor quis registrar explicitamente qual caminho (v4 ou v6) cada
conexão usou.

Em **Java a JVM já resolve isso sozinha**: desde o suporte a IPv6 introduzido
no Java 1.4, quando você abre um `ServerSocket` **sem especificar um endereço**
(bind no endereço curinga), a JVM cria um socket dual-stack que aceita
conexões IPv4 e IPv6 **na mesma porta, no mesmo socket** — isso é garantido
pela própria JVM (documentado no pacote `java.net`), não depende de
configuração do SO.

Ou seja: em vez de dois `ServerSocket`s (um v6, um v4) como no Node, vamos usar
**um único `ServerSocket`**, e detectar o protocolo de cada conexão assim:

```java
boolean isIPv4 = socket.getInetAddress() instanceof Inet4Address;
```

Isso simplifica bastante o código e no fim das contas dá o mesmo resultado
prático: o dispositivo consegue conectar tanto por IPv4 quanto por IPv6, e o
painel mostra corretamente qual protocolo foi usado.

> **Nota:** teste isso na plataforma de destino (Linux, que é o que vamos usar
> no Azure) antes de assumir 100% de paridade. Em ambientes muito restritos
> (containers com IPv6 desabilitado no kernel, por exemplo) pode ser necessário
> ajustar. Trataremos isso na Fase 11 se for o caso.

### A única mudança necessária no front-end

O `app.js` original conecta o WebSocket assim:

```js
const ws = new WebSocket(`${proto}://${location.host}`);
```

Ou seja, na raiz (`/`). No Spring, é mais limpo registrar o WebSocket num path
próprio (ex: `/ws`) para não conflitar com o roteamento de arquivos estáticos.
Então a única linha que vamos alterar no front-end original é essa:

```js
const ws = new WebSocket(`${proto}://${location.host}/ws`);
```

Fora isso, **HTML, CSS e a lógica toda do painel continuam idênticos**.

### Pré-requisitos gerais

- JDK 17 ou superior (`java -version`)
- Maven 3.8+ (`mvn -version`)
- Uma IDE (IntelliJ IDEA Community é a mais tranquila pra Spring Boot, mas
  VS Code com extensão Java funciona bem também)
- Mais adiante (Fase 11): conta Azure + Azure CLI instalado

### Estrutura final de pastas (prévia — vamos construir isso peça por peça)

```
cloud6/
├── pom.xml
├── src/main/java/com/transire/cloud6/
│   ├── Cloud6Application.java
│   ├── config/
│   │   └── WebSocketConfig.java
│   ├── event/
│   │   ├── TrafficEvent.java
│   │   ├── EventBus.java
│   │   ├── SnapshotMessage.java
│   │   └── DevicesSnapshotMessage.java
│   ├── ws/
│   │   └── TrafficWebSocketHandler.java
│   ├── echo/
│   │   └── EchoServer.java
│   ├── device/
│   │   ├── Device.java
│   │   ├── DeviceRegistry.java
│   │   └── DevicePingService.java
│   ├── api/
│   │   └── DeviceApiServer.java
│   └── bootstrap/
│       └── ServerBootstrap.java
└── src/main/resources/
    ├── application.properties
    └── static/
        ├── index.html
        ├── style.css
        ├── app.js
        └── images/logo.png
```

---

<a name="fase-1"></a>
## Fase 1 — Setup do projeto Spring Boot

### Objetivo
Ter um projeto Spring Boot rodando (mesmo que sem funcionalidade ainda) antes
de escrever qualquer lógica de negócio.

### Passo 1.1 — Conferir pré-requisitos

```bash
java -version
mvn -version
```

Precisa ser **Java 17+**. Se algum comando falhar, resolve a instalação antes
de seguir.

### Passo 1.2 — Estrutura de pastas

```bash
mkdir -p cloud6/src/main/java/com/transire/cloud6
mkdir -p cloud6/src/main/resources/static/images
cd cloud6
```

### Passo 1.3 — `pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.4</version>
    <relativePath/>
  </parent>

  <groupId>com.transire</groupId>
  <artifactId>cloud6</artifactId>
  <version>1.0.0</version>
  <name>Cloud6</name>
  <description>ECHO-6 — Monitor de Echo TCP/IPv6 + API de dispositivos (versão Java)</description>

  <properties>
    <java.version>17</java.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-websocket</artifactId>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

**Por quê essas duas dependências:** `spring-boot-starter-web` traz o Tomcat
embutido + Jackson (JSON). `spring-boot-starter-websocket` adiciona o suporte
a WebSocket do Spring. A API de dispositivos (porta 7777) **não** vai usar
Spring — vamos usar `com.sun.net.httpserver.HttpServer`, que já vem dentro do
próprio JDK, sem dependência nenhuma.

### Passo 1.4 — Classe principal

`src/main/java/com/transire/cloud6/Cloud6Application.java`:

```java
package com.transire.cloud6;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Cloud6Application {
    public static void main(String[] args) {
        SpringApplication.run(Cloud6Application.class, args);
    }
}
```

### Passo 1.5 — `application.properties`

`src/main/resources/application.properties`:

```properties
server.port=${WEB_PORT:3000}
```

Isso já deixa preparado o mesmo comportamento do Node: por padrão usa 3000,
mas se existir uma variável de ambiente `WEB_PORT`, ela tem prioridade.

### Passo 1.6 — Rodar

```bash
mvn spring-boot:run
```

Deve aparecer `Tomcat started on port 3000` no log. Abrir
`http://localhost:3000` — vai aparecer a "Whitelabel Error Page" do Spring, e
**isso é esperado** (ainda não tem nada em `static/`).

### ✅ Checklist de validação — Fase 1
- [ ] `mvn spring-boot:run` sobe sem erro
- [ ] Log mostra `Tomcat started on port 3000`
- [ ] `http://localhost:3000` responde (mesmo que com erro 404/whitelabel)

---

<a name="fase-2"></a>
## Fase 2 — Modelo de eventos e o EventBus

### Objetivo
Criar a estrutura de dados que representa cada evento do painel (conexão,
dado recebido, erro, registro de dispositivo, etc.) e o "barramento" que
guarda o histórico e distribui os eventos para todos os clientes WebSocket
conectados — equivalente ao `pushEvent`/`broadcast` do `server.js` original.

### Passo 2.1 — `TrafficEvent.java`

`src/main/java/com/transire/cloud6/event/TrafficEvent.java`:

```java
package com.transire.cloud6.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

// @JsonInclude(NON_NULL) faz o Jackson omitir campos nulos no JSON final —
// igual ao objeto "flexível" que o Node usa (cada tipo de evento só
// preenche os campos que faz sentido pra ele).
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

    // Setters "fluentes" (retornam this) só pra deixar a criação de eventos
    // legível em uma linha só, ex: TrafficEvent.of("connect").ip(x).port(y)
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

    // Getters — necessários pro Jackson conseguir serializar os campos em JSON
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
```

### Passo 2.2 — `SnapshotMessage.java` e `DevicesSnapshotMessage.java`

Essas duas classes representam as duas mensagens que o servidor manda assim
que um cliente WebSocket conecta (igual ao `ws.send(...)` duplo do
`wss.on("connection", ...)` no Node).

`src/main/java/com/transire/cloud6/event/SnapshotMessage.java`:

```java
package com.transire.cloud6.event;

import java.util.List;

public class SnapshotMessage {
    public String type = "snapshot";
    public int totalConnections;
    public int activeConnections;
    public List<TrafficEvent> history;
}
```

`src/main/java/com/transire/cloud6/event/DevicesSnapshotMessage.java`:

```java
package com.transire.cloud6.event;

import com.transire.cloud6.device.Device;
import java.util.List;

public class DevicesSnapshotMessage {
    public String type = "devices_snapshot";
    public List<Device> devices;
}
```

(A classe `Device` ainda não existe — vamos criá-la na Fase 6. Se sua IDE
reclamar de import quebrado agora, é normal, resolve sozinho quando chegarmos lá.)

### Passo 2.3 — `EventBus.java`

`src/main/java/com/transire/cloud6/event/EventBus.java`:

```java
package com.transire.cloud6.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class EventBus {

    private static final int HISTORY_LIMIT = 500;

    private final List<TrafficEvent> history = Collections.synchronizedList(new ArrayList<>());
    private final Set<WebSocketSession> sessions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ObjectMapper mapper = new ObjectMapper();

    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final AtomicInteger activeConnections = new AtomicInteger(0);

    public int incrementTotal() { return totalConnections.incrementAndGet(); }
    public int incrementActive() { return activeConnections.incrementAndGet(); }
    public int decrementActive() { return activeConnections.updateAndGet(v -> Math.max(0, v - 1)); }
    public int getTotalConnections() { return totalConnections.get(); }
    public int getActiveConnections() { return activeConnections.get(); }

    public List<TrafficEvent> historySnapshot() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    // Equivalente ao pushEvent() do server.js: guarda no histórico (limitado)
    // e manda pra todo mundo conectado.
    public void pushEvent(TrafficEvent event) {
        synchronized (history) {
            history.add(event);
            if (history.size() > HISTORY_LIMIT) history.remove(0);
        }
        broadcast(event);
    }

    public void registerSession(WebSocketSession session) { sessions.add(session); }
    public void unregisterSession(WebSocketSession session) { sessions.remove(session); }

    // Equivalente ao broadcast() do server.js
    public void broadcast(Object payload) {
        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (Exception e) {
            return;
        }
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) session.sendMessage(message);
            } catch (Exception ignored) {
                // sessão pode ter fechado entre a checagem e o envio — sem problema
            }
        }
    }
}
```

### ✅ Checklist de validação — Fase 2
- [ ] As 4 classes compilam (mesmo com o import de `Device` ainda não resolvido — isso é esperado até a Fase 6)
- [ ] Você entende por que `EventBus` é `@Component` (pra o Spring injetar a mesma instância em todo lugar que precisar dele — é o nosso "estado compartilhado", equivalente às variáveis `let totalConnections`, `history` etc. do topo do `server.js`)

---

<a name="fase-3"></a>
## Fase 3 — Servidor TCP Echo (porta 5000)

### Objetivo
Recriar o coração do app original: um servidor TCP que aceita conexões,
registra os eventos (connect/data/disconnect/error) e devolve exatamente os
bytes recebidos.

### Passo 3.1 — `EchoServer.java`

`src/main/java/com/transire/cloud6/echo/EchoServer.java`:

```java
package com.transire.cloud6.echo;

import com.transire.cloud6.event.EventBus;
import com.transire.cloud6.event.TrafficEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HexFormat;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class EchoServer {

    @Value("${echo.port:5000}")
    private int port;

    private final EventBus eventBus;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;

    public EchoServer(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void start() {
        executor.submit(this::acceptLoop);
    }

    private void acceptLoop() {
        try {
            // Bind sem endereço específico = endereço curinga = socket dual-stack
            // (aceita IPv4 e IPv6 na mesma porta — ver explicação na Fase 0)
            serverSocket = new ServerSocket(port);
            System.out.println("[ECHO] Servidor ECHO (IPv4+IPv6) ativo na porta " + port);

            while (!serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();
                executor.submit(() -> handleConnection(socket));
            }
        } catch (IOException e) {
            System.err.println("[ECHO] Erro no servidor: " + e.getMessage());
        }
    }

    private void handleConnection(Socket socket) {
        String ip = socket.getInetAddress().getHostAddress();
        int remotePort = socket.getPort();
        String family = (socket.getInetAddress() instanceof Inet4Address) ? "IPv4" : "IPv6";

        int total = eventBus.incrementTotal();
        int active = eventBus.incrementActive();

        eventBus.pushEvent(
            TrafficEvent.of("connect")
                .ip(ip).port(remotePort).family(family)
                .totalConnections(total).activeConnections(active)
        );

        try (socket;
             InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            byte[] buffer = new byte[8192];
            int n = in.read(buffer); // lê só o primeiro "pacote" — igual ao evento único "data" do Node

            if (n > 0) {
                byte[] received = Arrays.copyOf(buffer, n);
                String text = tryDecodeAsText(received);

                eventBus.pushEvent(
                    TrafficEvent.of("data")
                        .ip(ip).port(remotePort).family(family)
                        .text(text)
                        .hex(HexFormat.of().formatHex(received))
                        .bytes(n)
                );

                // ECHO REAL: devolve exatamente o que foi recebido
                out.write(received);
                out.flush();
            }
        } catch (IOException e) {
            eventBus.pushEvent(
                TrafficEvent.of("error")
                    .ip(ip).port(remotePort).family(family)
                    .message(e.getMessage())
            );
        } finally {
            int activeAfter = eventBus.decrementActive();
            eventBus.pushEvent(
                TrafficEvent.of("disconnect")
                    .ip(ip).port(remotePort).family(family)
                    .totalConnections(eventBus.getTotalConnections())
                    .activeConnections(activeAfter)
            );
        }
    }

    // Equivalente ao formatData() do server.js: só considera "texto" se for
    // tudo caracteres ASCII imprimíveis (0x20–0x7E) + espaço/quebra de linha/tab.
    private String tryDecodeAsText(byte[] data) {
        String text = new String(data, StandardCharsets.UTF_8);
        boolean printable = !text.isEmpty() && text.chars()
            .allMatch(c -> (c >= 0x20 && c <= 0x7E) || c == '\n' || c == '\r' || c == '\t');
        return printable ? text : null;
    }

    public void stop() {
        executor.shutdownNow();
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
    }
}
```

**Pontos de atenção pra quem tá começando em Java:**
- `try (socket; InputStream in = ...; OutputStream out = ...)` é um
  *try-with-resources* com múltiplos recursos — todos são fechados
  automaticamente ao sair do bloco, na ordem inversa em que foram abertos.
  Isso substitui o `socket.on("close", ...)` do Node.
- Cada conexão aceita roda numa thread separada do `ExecutorService`
  (`executor.submit(...)`) — é o equivalente Java ao modelo assíncrono do
  Node (que processa tudo numa única thread com callbacks); aqui usamos
  várias threads, cada uma bloqueante, o que é o padrão idiomático em Java.

### ✅ Checklist de validação — Fase 3
- [ ] O código compila (mesmo sem estar sendo chamado ainda em lugar nenhum — vamos ligar isso na Fase 8)
- [ ] Você entende a diferença entre o modelo de threads do Java (uma thread por conexão) e o modelo do Node (single-thread + callbacks)

---

<a name="fase-4"></a>
## Fase 4 — WebSocket do painel

### Objetivo
Ligar o painel web ao `EventBus`: quando alguém abre o painel no navegador, o
servidor manda o snapshot atual (histórico + contadores + dispositivos); e
cada novo evento é transmitido em tempo real.

### Passo 4.1 — `TrafficWebSocketHandler.java`

`src/main/java/com/transire/cloud6/ws/TrafficWebSocketHandler.java`:

```java
package com.transire.cloud6.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.transire.cloud6.device.DevicePingService;
import com.transire.cloud6.device.DeviceRegistry;
import com.transire.cloud6.event.DevicesSnapshotMessage;
import com.transire.cloud6.event.EventBus;
import com.transire.cloud6.event.SnapshotMessage;
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
        snapshot.totalConnections = eventBus.getTotalConnections();
        snapshot.activeConnections = eventBus.getActiveConnections();
        snapshot.history = eventBus.historySnapshot();
        session.sendMessage(new TextMessage(mapper.writeValueAsString(snapshot)));

        DevicesSnapshotMessage devicesSnapshot = new DevicesSnapshotMessage();
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
```

(De novo: `DeviceRegistry` e `DevicePingService` ainda não existem — vêm na
Fase 6. Import quebrado agora é esperado.)

### Passo 4.2 — `WebSocketConfig.java`

`src/main/java/com/transire/cloud6/config/WebSocketConfig.java`:

```java
package com.transire.cloud6.config;

import com.transire.cloud6.ws.TrafficWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TrafficWebSocketHandler handler;

    public WebSocketConfig(TrafficWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws").setAllowedOrigins("*");
    }
}
```

Isso regista o WebSocket em `/ws` — é por isso que, na Fase 5, vamos ajustar
a única linha do `app.js` original.

### ✅ Checklist de validação — Fase 4
- [ ] Entende o papel de cada método: `afterConnectionEstablished` (equivalente ao `wss.on("connection", ws => {...})`), `handleTextMessage` (equivalente ao `ws.on("message", ...)`), `afterConnectionClosed` (limpeza quando desconecta)
- [ ] Sabe que o path `/ws` foi escolhido pra não conflitar com os arquivos estáticos servidos na raiz

---

<a name="fase-5"></a>
## Fase 5 — Front-end (HTML/CSS/JS)

### Objetivo
Copiar o painel original quase sem alterações.

### Passo 5.1 — Copiar arquivos

Copie do projeto Node original para `src/main/resources/static/`:
- `index.html` → sem nenhuma alteração
- `style.css` → sem nenhuma alteração
- `images/logo.png` → sem nenhuma alteração
- `app.js` → **com uma linha alterada** (abaixo)

### Passo 5.2 — A única alteração necessária

Em `app.js`, localize:

```js
const ws = new WebSocket(`${proto}://${location.host}`);
```

E troque por:

```js
const ws = new WebSocket(`${proto}://${location.host}/ws`);
```

Só isso. O resto da lógica do painel (renderização da tabela, filtro por IP,
cards de dispositivo, relógio, etc.) continua **exatamente igual**.

### ✅ Checklist de validação — Fase 5
- [ ] Os 4 arquivos estão em `src/main/resources/static/` (com `images/logo.png` dentro de uma subpasta)
- [ ] A única diferença em relação ao original é a linha do `WebSocket(...)`

---

<a name="fase-6"></a>
## Fase 6 — Registro e API de dispositivos (porta 7777)

### Objetivo
Recriar o endpoint que o app Android usa: registrar o dispositivo, responder
a um ping manual, listar dispositivos, e fazer o "ping de volta" pro celular.

### Passo 6.1 — `Device.java`

`src/main/java/com/transire/cloud6/device/Device.java`:

```java
package com.transire.cloud6.device;

public class Device {
    private String name;
    private String ip;
    private int replyPort;
    private String family;
    private String registeredAt;
    private String lastSeen;
    private String lastPingBackAt;
    private String lastPingBackStatus = "pendente";
    private String lastPingBackMessage = "";

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getIp() { return ip; }
    public void setIp(String v) { this.ip = v; }
    public int getReplyPort() { return replyPort; }
    public void setReplyPort(int v) { this.replyPort = v; }
    public String getFamily() { return family; }
    public void setFamily(String v) { this.family = v; }
    public String getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(String v) { this.registeredAt = v; }
    public String getLastSeen() { return lastSeen; }
    public void setLastSeen(String v) { this.lastSeen = v; }
    public String getLastPingBackAt() { return lastPingBackAt; }
    public void setLastPingBackAt(String v) { this.lastPingBackAt = v; }
    public String getLastPingBackStatus() { return lastPingBackStatus; }
    public void setLastPingBackStatus(String v) { this.lastPingBackStatus = v; }
    public String getLastPingBackMessage() { return lastPingBackMessage; }
    public void setLastPingBackMessage(String v) { this.lastPingBackMessage = v; }
}
```

### Passo 6.2 — `DeviceRegistry.java`

`src/main/java/com/transire/cloud6/device/DeviceRegistry.java`:

```java
package com.transire.cloud6.device;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DeviceRegistry {
    // Equivalente ao Map<name, device> do server.js — ConcurrentHashMap
    // porque vai ser acessado por várias threads ao mesmo tempo (echo, API, WebSocket)
    private final Map<String, Device> devices = new ConcurrentHashMap<>();

    public Device get(String name) { return devices.get(name); }
    public void put(Device device) { devices.put(device.getName(), device); }
    public List<Device> snapshot() { return new ArrayList<>(devices.values()); }
}
```

### Passo 6.3 — `DevicePingService.java`

`src/main/java/com/transire/cloud6/device/DevicePingService.java`:

```java
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

    // "Fire and forget": quem chama não espera o resultado — igual ao
    // pingBackAndReport(device) do Node, que não é aguardado (await) por quem chama.
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
```

### Passo 6.4 — `DeviceApiServer.java`

`src/main/java/com/transire/cloud6/api/DeviceApiServer.java`:

```java
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
        // Mesmo raciocínio do EchoServer: bind sem endereço = dual-stack
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
            sendJson(exchange, 405, Map.of("message", "método não permitido"));
            return;
        }

        Map<?, ?> body = mapper.readValue(exchange.getRequestBody(), Map.class);
        String name = (String) body.get("name");
        Object replyPortObj = body.get("replyPort");

        if (name == null || replyPortObj == null) {
            sendJson(exchange, 400, Map.of("message", "name e replyPort são obrigatórios"));
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

        // Completa o ping-pong de forma assíncrona — não bloqueia a resposta acima
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
```

### ✅ Checklist de validação — Fase 6
- [ ] As 4 classes compilam sem erro
- [ ] Você entende por que `DeviceApiServer` não usa Spring MVC (`@RestController`) — é porque ele precisa rodar numa porta HTTP *separada* (7777) e independente do painel (3000), então usamos o `HttpServer` cru do JDK em vez do Tomcat gerenciado pelo Spring

---

<a name="fase-7"></a>
## Fase 7 — Configuração (properties + variáveis de ambiente)

### Objetivo
Preservar o mesmo comportamento de configuração do Node original — três
variáveis de ambiente (`ECHO_PORT`, `WEB_PORT`, `API_PORT`) com os mesmos
valores padrão.

### `application.properties` final

`src/main/resources/application.properties`:

```properties
# Porta do painel web (Tomcat) — igual ao WEB_PORT do server.js original
server.port=${WEB_PORT:3000}

# Porta do servidor de echo TCP — igual ao ECHO_PORT
echo.port=${ECHO_PORT:5000}

# Porta da API de dispositivos — igual ao API_PORT
api.port=${API_PORT:7777}
```

**Como isso funciona:** `${WEB_PORT:3000}` é um placeholder do Spring — ele
procura por uma variável de ambiente ou propriedade de sistema chamada
`WEB_PORT`; se não encontrar, usa `3000` como padrão. Isso preserva
exatamente a mesma forma de configurar que o app Node já usava:

```bash
ECHO_PORT=6000 WEB_PORT=8080 API_PORT=7777 java -jar cloud6-1.0.0.jar
```

### ✅ Checklist de validação — Fase 7
- [ ] Rodando sem variáveis de ambiente, usa as portas padrão (3000/5000/7777)
- [ ] Rodando com `ECHO_PORT=6000 mvn spring-boot:run` (ou equivalente), o log do `EchoServer` mostra a porta 6000

---

<a name="fase-8"></a>
## Fase 8 — Orquestração de inicialização

### Objetivo
Ligar o `EchoServer` e o `DeviceApiServer` para começarem a rodar assim que o
Spring Boot terminar de subir (senão eles nunca são chamados — até agora só
criamos as classes, mas nada as inicia).

### `ServerBootstrap.java`

`src/main/java/com/transire/cloud6/bootstrap/ServerBootstrap.java`:

```java
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

    // Dispara quando o Spring Boot termina de subir TUDO (contexto pronto,
    // Tomcat escutando) — é o momento certo pra ligar os outros dois servidores.
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() throws Exception {
        echoServer.start();
        deviceApiServer.start();
    }
}
```

**Por que não iniciar isso no `main()` ou num `@PostConstruct` direto:**
`ApplicationReadyEvent` garante que todo o contexto Spring (incluindo o
`EventBus`, `DeviceRegistry`, WebSocket etc.) já está 100% inicializado antes
de começarmos a aceitar conexões TCP reais — evita corridas (race conditions)
na subida da aplicação.

### ✅ Checklist de validação — Fase 8
- [ ] `mvn spring-boot:run` mostra as 3 linhas de log: Tomcat (porta 3000), `[ECHO]` (porta 5000), `[API]` (porta 7777)
- [ ] Nenhum erro de "port already in use"

---

<a name="fase-9"></a>
## Fase 9 — Testes locais completos

Com tudo rodando (`mvn spring-boot:run`), rode estes testes numa ordem lógica:

### 9.1 — Painel abre
Abra `http://localhost:3000` — deve carregar o painel completo (tabela, sidebar, LEDs).

### 9.2 — Echo via IPv4
```bash
echo -n "SN-0042:Temperatura 23.5C" | nc 127.0.0.1 5000
```
No painel, deve aparecer uma linha `connect`, uma `data` (com Serial Number
`SN-0042` e Dados `Temperatura 23.5C`), e uma `disconnect` — com o selo
**IPv4**.

### 9.3 — Echo via IPv6
```bash
echo -n "SN-0099:teste ipv6" | nc -6 ::1 5000
```
Mesma coisa, mas com selo **IPv6**.

### 9.4 — Registro de dispositivo (simulando o app Android)
```bash
curl -X POST http://localhost:7777/api/devices/ping \
  -H "Content-Type: application/json" \
  -d '{"name":"celular-teste","replyPort":9999}'
```
Deve aparecer o dispositivo na sidebar. Como não existe nenhum mini-servidor
de verdade escutando na porta 9999, o ping-pong deve falhar (LED vermelho,
status "falha") — **isso é o comportamento esperado** desse teste, valida o
caminho de erro.

### 9.5 — Ping manual
```bash
curl http://localhost:7777/api/ping
```
Deve responder `{"message":"pong", ...}` e aparecer no painel como `api_ping`.

### 9.6 — Listagem de dispositivos
```bash
curl http://localhost:7777/api/devices
```
Deve retornar um JSON com o `celular-teste` registrado no passo 9.4.

### 9.7 — Filtro por IP no painel
Digite `127.0.0.1` ou `fd00` (ou parte de algum IP que apareceu) no campo de
filtro do painel e confirme que só as linhas correspondentes ficam visíveis.

### ✅ Checklist de validação — Fase 9
- [ ] Todos os 7 testes acima passaram
- [ ] O painel atualiza em tempo real (sem precisar dar F5) em todos os casos

---

<a name="fase-10"></a>
## Fase 10 — Empacotamento (JAR executável)

### Passo 10.1 — Gerar o JAR

```bash
mvn clean package
```

Isso gera `target/cloud6-1.0.0.jar` — um JAR **executável** (o
`spring-boot-maven-plugin` empacota todas as dependências dentro dele, então
não precisa de classpath externo).

### Passo 10.2 — Rodar o JAR

```bash
java -jar target/cloud6-1.0.0.jar
```

Com variáveis de ambiente customizadas:

```bash
ECHO_PORT=6000 WEB_PORT=8080 API_PORT=7777 java -jar target/cloud6-1.0.0.jar
```

### ✅ Checklist de validação — Fase 10
- [ ] `java -jar target/cloud6-1.0.0.jar` sobe sozinho, sem precisar do Maven
- [ ] Repetir os testes da Fase 9 contra o JAR (não só via `mvn spring-boot:run`)

---

<a name="fase-11"></a>
## Fase 11 — Deploy no Azure

### Por que uma VM, e não um Azure App Service "comum"

O Azure App Service (PaaS) é feito pra apps **HTTP(S) numa única porta
pública** (80/443, com proxy reverso). Esse app abre **três portas TCP
distintas e brutas** (5000, 7777, além da 3000 do painel) — isso não é o
modelo que o App Service suporta de forma nativa e simples.

Como você já rodava o `server.py` original numa **VM Azure**, o caminho mais
direto e fiel é continuar com uma VM Linux.

### Passo 11.1 — Provisionar a VM (se ainda não tiver uma)

```bash
az group create --name rg-cloud6 --location brazilsouth

az vm create \
  --resource-group rg-cloud6 \
  --name vm-cloud6 \
  --image Ubuntu2204 \
  --size Standard_B2s \
  --admin-username azureuser \
  --generate-ssh-keys
```

### Passo 11.2 — Abrir as portas no Network Security Group

```bash
az vm open-port --resource-group rg-cloud6 --name vm-cloud6 --port 3000 --priority 1010
az vm open-port --resource-group rg-cloud6 --name vm-cloud6 --port 5000 --priority 1011
az vm open-port --resource-group rg-cloud6 --name vm-cloud6 --port 7777 --priority 1012
```

### Passo 11.3 — Instalar Java na VM

```bash
ssh azureuser@<ip-da-vm>
sudo apt update
sudo apt install -y openjdk-17-jre-headless
java -version
```

### Passo 11.4 — Enviar o JAR pra VM

Da sua máquina local:

```bash
scp target/cloud6-1.0.0.jar azureuser@<ip-da-vm>:/home/azureuser/
```

Na VM:

```bash
sudo mkdir -p /opt/cloud6
sudo mv /home/azureuser/cloud6-1.0.0.jar /opt/cloud6/
```

### Passo 11.5 — Rodar como serviço systemd (fica de pé mesmo se a VM reiniciar)

Na VM, criar `/etc/systemd/system/cloud6.service`:

```ini
[Unit]
Description=Cloud6 ECHO-6 (Java)
After=network.target

[Service]
User=azureuser
Environment=ECHO_PORT=5000
Environment=WEB_PORT=3000
Environment=API_PORT=7777
ExecStart=/usr/bin/java -jar /opt/cloud6/cloud6-1.0.0.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

Ativar:

```bash
sudo systemctl daemon-reload
sudo systemctl enable cloud6
sudo systemctl start cloud6
sudo systemctl status cloud6
```

Ver logs:

```bash
journalctl -u cloud6 -f
```

### Nota sobre IPv6 público no Azure

Ter o socket dual-stack funcionando **dentro da VM** (que é o que fizemos até
aqui) é diferente de ter **um IP público IPv6** alcançável de fora — isso
depende de a VNet estar configurada como dual-stack e a VM ter um IP público
IPv6 associado, o que envolve configuração de rede específica do Azure (fora
do escopo do código Java). Se seus dispositivos precisarem alcançar a VM via
IPv6 público de verdade (não só localmente/rede privada), vale tratar isso
como uma sub-fase própria — me avisa quando chegar nesse ponto que a gente
destrincha certinho.

### ✅ Checklist de validação — Fase 11
- [ ] `systemctl status cloud6` mostra `active (running)`
- [ ] Painel acessível em `http://<ip-da-vm>:3000`
- [ ] Testes de echo (nc) e API de dispositivos (curl) funcionando contra o IP público da VM
- [ ] Serviço volta sozinho depois de `sudo reboot` na VM

---

## Resumo de arquivos criados

```
cloud6/
├── pom.xml                                              (Fase 1)
├── src/main/java/com/transire/cloud6/
│   ├── Cloud6Application.java                           (Fase 1)
│   ├── config/WebSocketConfig.java                      (Fase 4)
│   ├── event/TrafficEvent.java                          (Fase 2)
│   ├── event/EventBus.java                              (Fase 2)
│   ├── event/SnapshotMessage.java                       (Fase 2)
│   ├── event/DevicesSnapshotMessage.java                (Fase 2)
│   ├── ws/TrafficWebSocketHandler.java                  (Fase 4)
│   ├── echo/EchoServer.java                             (Fase 3)
│   ├── device/Device.java                               (Fase 6)
│   ├── device/DeviceRegistry.java                       (Fase 6)
│   ├── device/DevicePingService.java                    (Fase 6)
│   ├── api/DeviceApiServer.java                         (Fase 6)
│   └── bootstrap/ServerBootstrap.java                   (Fase 8)
└── src/main/resources/
    ├── application.properties                           (Fase 7)
    └── static/ (index.html, style.css, app.js, images/logo.png)  (Fase 5)
```
