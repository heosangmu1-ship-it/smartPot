package com.garden.smartgarden.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.garden.smartgarden.domain.SensorReading;
import com.garden.smartgarden.influx.InfluxWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * MQTT 브로커에서 들어온 메시지를 처리하는 중앙 핸들러.
 *
 * Fan-out 패턴:
 *  1) Redis Pub/Sub 에 발행   -> 모든 Spring Boot 인스턴스의 SSE 로 전파
 *  2) Redis Hash 에 현재값 저장 -> REST API 즉시 응답용 캐시
 *  3) InfluxDB 에 이력 저장     -> 시계열 분석용
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttMessageHandler {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final InfluxWriter influxWriter;

    @Value("${app.redis.pubsub-channel}")
    private String pubsubChannel;

    @Value("${app.redis.current-key-prefix}")
    private String currentKeyPrefix;

    private static final String CURRENT_HASH_KEY = "sensor:current";
    private static final String STATUS_KEY = "device:status";

    public void handle(Message<?> message) throws MessagingException {
        String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
        String payload = message.getPayload().toString();

        if (topic == null) return;

        try {
            if ("garden/status".equals(topic)) {
                handleStatus(payload);
                return;
            }

            // garden/sensor/{metric}/state 파싱
            Optional<String> metric = parseMetric(topic);
            if (metric.isEmpty()) return;

            double value = Double.parseDouble(payload);
            var reading = SensorReading.builder()
                    .metric(metric.get())
                    .value(value)
                    .timestamp(Instant.now())
                    .deviceId("garden-sensor-01")
                    .build();

            fanOut(reading);
        } catch (NumberFormatException e) {
            log.debug("Non-numeric payload on {}: {}", topic, payload);
        } catch (Exception e) {
            log.error("Error handling MQTT message topic={} payload={}", topic, payload, e);
        }
    }

    private void handleStatus(String payload) {
        redis.opsForValue().set(STATUS_KEY, payload);
        log.info("Device status: {}", payload);

        if ("offline".equalsIgnoreCase(payload)) {
            // offline 이면 현재값 캐시 제거 (유령 데이터 방지)
            redis.delete(CURRENT_HASH_KEY);
        }
    }

    private void fanOut(SensorReading reading) throws Exception {
        // (1) 현재값 캐시 업데이트
        redis.opsForHash().put(CURRENT_HASH_KEY, reading.getMetric(), reading.getValue().toString());
        redis.opsForValue().set(currentKeyPrefix + reading.getMetric(), reading.getValue().toString());

        // (2) Redis Pub/Sub 발행 -> SSE 브로드캐스트 트리거
        String json = objectMapper.writeValueAsString(reading);
        redis.convertAndSend(pubsubChannel, json);

        // (3) InfluxDB 이력 저장 (비동기)
        influxWriter.writeAsync(reading);

        log.debug("Fanned out: {}={}", reading.getMetric(), reading.getValue());
    }

    private Optional<String> parseMetric(String topic) {
        // garden/sensor/{metric}/state
        String[] parts = topic.split("/");
        if (parts.length == 4 && "garden".equals(parts[0]) && "sensor".equals(parts[1]) && "state".equals(parts[3])) {
            return Optional.of(parts[2]);
        }
        return Optional.empty();
    }
}
