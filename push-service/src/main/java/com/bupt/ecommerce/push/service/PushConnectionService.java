package com.bupt.ecommerce.push.service;

import com.bupt.ecommerce.common.api.ApiResponse;
import com.bupt.ecommerce.push.dto.OrderPushEvent;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class PushConnectionService {

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        emittersByUser.computeIfAbsent(userId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(ignored -> remove(userId, emitter));

        sendConnected(userId, emitter);
        return emitter;
    }

    public int publish(OrderPushEvent event) {
        List<SseEmitter> emitters = emittersByUser.getOrDefault(event.userId(), new CopyOnWriteArrayList<>());
        int delivered = 0;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("order-result")
                        .data(ApiResponse.success(event), MediaType.APPLICATION_JSON));
                delivered++;
            } catch (IOException ex) {
                remove(event.userId(), emitter);
            }
        }
        return delivered;
    }

    private void sendConnected(Long userId, SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(ApiResponse.success(Map.of(
                            "status", "CONNECTED",
                            "userId", userId
                    )), MediaType.APPLICATION_JSON));
        } catch (IOException ex) {
            remove(userId, emitter);
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByUser.remove(userId);
        }
    }
}
