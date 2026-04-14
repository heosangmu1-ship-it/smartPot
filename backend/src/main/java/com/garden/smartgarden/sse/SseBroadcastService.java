package com.garden.smartgarden.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE 연결을 인메모리로 관리하면서,
 * Redis Pub/Sub 에서 넘어온 이벤트를 로컬 emitter 들에게 브로드캐스트.
 *
 * Redis Pub/Sub 과의 결합:
 *  - RedisConfig 가 이 클래스의 onRedisMessage(String) 메서드를 메시지 리스너로 등록
 *  - Redis 채널에 PUBLISH 되면 자동으로 호출됨
 */
@Slf4j
@Service
public class SseBroadcastService {

    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L; // 30분

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        var emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.add(emitter);

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.debug("SSE completed. Active emitters: {}", emitters.size());
        });
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            log.debug("SSE timeout. Active emitters: {}", emitters.size());
        });
        emitter.onError(ex -> {
            emitters.remove(emitter);
            log.debug("SSE error. Active emitters: {}", emitters.size());
        });

        // 초기 접속 알림 (클라이언트가 연결 성공을 확인할 수 있도록)
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        log.info("New SSE subscriber. Active emitters: {}", emitters.size());
        return emitter;
    }

    /**
     * RedisConfig 의 MessageListenerAdapter 가 이 이름("onRedisMessage")으로 호출.
     * MQTT → MqttMessageHandler → Redis PUBLISH → 이 메서드 → SSE push.
     */
    public void onRedisMessage(String jsonPayload) {
        log.debug("Broadcasting to {} emitters: {}", emitters.size(), jsonPayload);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("sensor")
                        .data(jsonPayload));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }

    public int getActiveSubscriberCount() {
        return emitters.size();
    }
}
