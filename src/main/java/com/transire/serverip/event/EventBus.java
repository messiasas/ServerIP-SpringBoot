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
* Evento aconteceu
       ↓
   pushEvent()
       ↓
  salva histórico
       ↓
    broadcast()
       ↓
 ┌─────┼─────┐
 ↓     ↓     ↓
 A     B     C
* */

public class EventBus{

    private static final int HISTORY_LIMIT = 500;

                                            // Porque seu servidor WebSocket pode ter várias threads simultaneamente.
    private final List<EventsTraffic> history = Collections.synchronizedList(new ArrayList<>());

    private final Set<WebSocketSession> sessions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ObjectMapper mapper = new ObjectMapper();

    // AtomicInteger -> support multithread
    private final AtomicInteger totConnections = new AtomicInteger(0);
    private final AtomicInteger activeConnections = new AtomicInteger(0);

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

    public List<EventsTraffic> historySnapshot(){
        synchronized (history){
            return new ArrayList<>(history);
        }
    }

    public void pushEvent(EventsTraffic event){
        synchronized (history){
            history.add(event);
            if(history.size() > HISTORY_LIMIT) history.remove(0);
        }
        broadCast(event);
    }

    public void registerSession(WebSocketSession session){
        sessions.add(session);
    }
    public void unregisterSession(WebSocketSession session){
        sessions.add(session);
    }

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