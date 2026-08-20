# Cloud6 (ECHO-6) — versão Java / Spring Boot

Projeto completo, montado seguindo as Fases 1 a 8 do guia de migração.

## O que tem aqui

- **Echo TCP** na porta `5000` (IPv4+IPv6 dual-stack)
- **API de dispositivos** na porta `7777` (registro, ping manual, listagem)
- **Painel web** na porta `3000` (Spring Boot + WebSocket em `/ws`)

## Pré-requisitos na sua máquina

```bash
java -version   # precisa ser 17 ou superior
mvn -version    # Maven 3.8+
```

Se `mvn` não existir, instale o Maven:
- **macOS**: `brew install maven`
- **Ubuntu/Debian**: `sudo apt install maven`
- **Windows**: baixe em https://maven.apache.org/download.cgi e adicione o `bin/` ao PATH

## Como rodar

1. Descompacte o zip em qualquer pasta.
2. Abra um terminal dentro da pasta `cloud6/` (onde está o `pom.xml`).
3. Rode:

```bash
mvn spring-boot:run
```

Na primeira vez o Maven vai baixar o Spring Boot e as dependências (precisa de internet). Depois disso fica em cache local (`~/.m2`) e sobe rápido.

Você deve ver no log:
```
[ECHO] Servidor ECHO (IPv4+IPv6) ativo na porta 5000
[API] API de dispositivos (IPv4+IPv6) ativa na porta 7777
Tomcat started on port(s): 3000
```

4. Abra `http://localhost:3000` no navegador — o painel carrega.

## Testando cada peça (sem precisar do navegador)

```bash
# Echo via IPv4
echo -n "SN-0042:Temperatura 23.5C" | nc 127.0.0.1 5000

# Echo via IPv6
echo -n "SN-0099:teste ipv6" | nc -6 ::1 5000

# Registro de dispositivo (simula o app Android)
curl -X POST http://localhost:7777/api/devices/ping \
  -H "Content-Type: application/json" \
  -d '{"name":"celular-teste","replyPort":9999}'

# Ping manual
curl http://localhost:7777/api/ping

# Listagem de dispositivos
curl http://localhost:7777/api/devices
```

Enquanto isso, no painel (ou com `websocat ws://localhost:3000/ws`), você vê os eventos chegando em tempo real.

## Rodando com portas customizadas

```bash
ECHO_PORT=6000 WEB_PORT=8080 API_PORT=7777 mvn spring-boot:run
```

## Gerando o JAR executável (empacotamento)

```bash
mvn clean package
java -jar target/cloud6-1.0.0.jar
```

## Sobre o front-end (`src/main/resources/static/`)

O guia original manda copiar `index.html`, `style.css`, `app.js` e `images/logo.png`
do projeto Node — mas esses arquivos originais não estavam disponíveis aqui, então
o `index.html`/`style.css`/`app.js` deste projeto foram escritos do zero para
implementar exatamente o que o backend expõe: snapshot inicial, tabela de eventos
em tempo real, filtro por IP, sidebar de dispositivos com botão de ping, contador
de conexões e relógio. Se você tiver os arquivos originais do painel Node, pode
simplesmente substituir os 3 arquivos em `static/` — só cuidado para manter a
linha do WebSocket apontando para `/ws` (ver Fase 5 do guia).

## Estrutura

```
cloud6/
├── pom.xml
├── src/main/java/com/transire/cloud6/
│   ├── Cloud6Application.java         (Fase 1)
│   ├── event/                        (Fase 2 — TrafficEvent, EventBus, mensagens)
│   ├── echo/EchoServer.java          (Fase 3)
│   ├── ws/, config/                  (Fase 4 — WebSocket)
│   ├── device/, api/                 (Fase 6 — dispositivos + API porta 7777)
│   └── bootstrap/ServerBootstrap.java (Fase 8 — liga tudo)
└── src/main/resources/
    ├── application.properties        (Fase 7)
    └── static/ (index.html, style.css, app.js)
```
