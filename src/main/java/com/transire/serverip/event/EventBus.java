package com.transire.serverip.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;


/*

1. Dispositivo conecta
       ↓
2. registerSession(session)          [Seção 5] → sessão entra no Set
       ↓
3. incrementTotal() / incrementActive() [Seção 2] → contadores sobem
       ↓
4. pushEvent(TrafficEvent.of("CONNECTED")) [Seção 4]
       ↓
5.    → history.add(event)           [dentro da Seção 4]
       → broadcast(event)            [Seção 6]
              ↓
6.            → mapper converte pra JSON
              → percorre "sessions" e envia pra cada uma [usa o Set da Seção 1/5]

 */
public class EventBus{

    // == DECLARAÇÃO DE ONDE OS DADOS VAO PERTENCER ==
    private static final int HISTORY_LIMIT = 500;

                                                // Because our app support multiple clients
    private final List<EventsTraffic> history = Collections.synchronizedList(new ArrayList<>());
                // conjunto Set que guarda as sessões websocket ativas
    private final Set<WebSocketSession> sessions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    // main class of Jackson - Responsible for convert java objects on JSON
    private final ObjectMapper mapper = new ObjectMapper();

    // AtomicInteger -> support multithread
    private final AtomicInteger totConnections = new AtomicInteger(0);
    private final AtomicInteger activeConnections = new AtomicInteger(0);

    // ===== Contadores de Conexão (onde o AtomicInteger entra em ação) =====
    // São os "botões" que outras partes do código apertam pra atualizar as estatísticas.
    public int incrementTotal(){
        return totConnections.incrementAndGet();
    }
    public int incrementActive(){
        return activeConnections.incrementAndGet();
    }
    public int decrementActive(){
        return activeConnections.updateAndGet(v-> Math.max(0, v-1));
    }
    public int getTotalConnections(){
        return totConnections.get();
    }
    public int getActiveConnections(){
        return activeConnections.get();
    }

    /* Quando um cliente novo conecta no painel, ele precisa ver os eventos que já aconteceram antes dele chegar. Esse método devolve uma cópia da lista de
     histórico naquele instante (chamada de "snapshot", ou seja, "foto" do estado atual)*/
    public List<EventsTraffic> historySnapshot(){
        synchronized (history){
            return new ArrayList<>(history);
        }
    }
    // Equivalente ao pushEvent() do server.js: guarda no histórico (limitado)
    // e manda pra todo mundo conectado.

    /*
    * Papel: é a função que qualquer parte do sistema chama quando algo aconteceu (conexão, erro, dado recebido). Ela faz duas coisas, em ordem:
    Guarda o evento no histórico (removendo o mais antigo se passar de 500)
    Chama broadcast() (Seção 6) pra mandar esse evento pra todo mundo conectado*/
    public void pushEvent(EventsTraffic event){
        synchronized (history){
            history.add(event);
            if(history.size() > HISTORY_LIMIT) history.remove(0);
        }
        broadCast(event);
    }

    // Gerenciamento de Sessões (quem está conectado agora)
    /*
    * Papel: é aqui que a lista de "quem está ouvindo" é atualizada. Isso é chamado pelo WebSocketHandler (arquivo separado, que ainda vamos construir) nos momentos de conectar/desconectar.

    É exatamente aqui que a Seção 2 entra em cena — o fluxo real, no handler do WebSocket, ficaria mais ou menos assim:*/

    /*
    * @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        eventBus.registerSession(session);      // Seção 5
        eventBus.incrementTotal();              // Seção 2
        eventBus.incrementActive();             // Seção 2
        eventBus.pushEvent(TrafficEvent.of("CONNECTED")); // Seção 4
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        eventBus.unregisterSession(session);    // Seção 5
        eventBus.decrementActive();             // Seção 2
        eventBus.pushEvent(TrafficEvent.of("DISCONNECTED")); // Seção 4
    }
    *
    * */
    public void registerSession(WebSocketSession session){
        sessions.add(session);
    }
    public void unregisterSession(WebSocketSession session){
        sessions.add(session);
    }

    /*Papel: converte o evento em JSON (usando o mapper da Seção 1) e percorre todas as
     sessões ativas (a Set da Seção 1, mantida atualizada pela Seção 5), enviando a mensagem pra cada uma.*/

    public void broadCast(Object payload){
        String json;
        try{
            json = mapper.writeValueAsString((payload));
        }catch(Exception e){
            return;
        }
        TextMessage message = new TextMessage(json);
        for(WebSocketSession session: sessions){
            try{
                if(session.isOpen()) session.sendMessage(message);
            }catch(Exception ignored){
                // sessao pode ter fechado entre a checagem e o envio - sem problema;
            }
        }
    }
}