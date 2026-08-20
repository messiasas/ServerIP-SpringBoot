package com.transire.serverip.echo;

import com.transire.serverip.event.EventBus;
import com.transire.serverip.event.EventsTraffic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class EchoServer {
    @Value("${echo.por:5000}")
    private int port;

    private final EventBus eventBus;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;

    public EchoServer(EventBus eventBus){
        this.eventBus = eventBus;
    }
    public void start(){
        executor.submit(this::acceptLoop);
    }

    private void acceptLoop(){
        try{
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

        eventBus.pushEvent(EventsTraffic.of("connecet")
                .ip(ip).port(remotePort).family(family)
                .totConnections(total).activeConnections(active)
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
                        EventsTraffic.of("data")
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
                    EventsTraffic.of("error")
                            .ip(ip).port(remotePort).family(family)
                            .message(e.getMessage())
            );
        } finally {
            int activeAfter = eventBus.decrementActive();
            eventBus.pushEvent(
                    EventsTraffic.of("disconnect")
                            .ip(ip).port(remotePort).family(family)
                            .totConnections(eventBus.getTotalConnections())
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
