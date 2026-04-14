package com.garden.smartgarden.api;

import com.garden.smartgarden.domain.SensorReading;
import com.garden.smartgarden.influx.InfluxQueryService;
import com.garden.smartgarden.sse.SseBroadcastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 센서 관련 REST + SSE API.
 *
 * GET /api/sensors/current       - 현재 모든 센서 값 (Redis)
 * GET /api/sensors/history       - 특정 메트릭의 이력 (InfluxDB)
 * GET /api/sensors/stream (SSE)  - 실시간 스트림
 * GET /api/device/status         - 디바이스 online/offline
 */
@Tag(name = "Sensors", description = "센서 현재값/이력/실시간 스트림 API")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SensorController {

    private final StringRedisTemplate redis;
    private final SseBroadcastService sseBroadcastService;
    private final InfluxQueryService influxQueryService;

    private static final String CURRENT_HASH_KEY = "sensor:current";
    private static final String STATUS_KEY = "device:status";

    @Operation(summary = "현재 센서 값", description = "Redis 캐시에서 최신 센서 값 전체를 한 번에 반환")
    @GetMapping("/sensors/current")
    public Map<String, Object> current() {
        Map<Object, Object> raw = redis.opsForHash().entries(CURRENT_HASH_KEY);
        Map<String, Object> result = new HashMap<>();
        raw.forEach((k, v) -> {
            try {
                result.put(k.toString(), Double.parseDouble(v.toString()));
            } catch (NumberFormatException ignored) {
                result.put(k.toString(), v.toString());
            }
        });
        result.put("status", Optional.ofNullable(redis.opsForValue().get(STATUS_KEY)).orElse("unknown"));
        result.put("timestamp", Instant.now().toString());
        return result;
    }

    @Operation(summary = "센서 이력 조회", description = "InfluxDB 3 Core 에서 시계열 데이터를 조회. 기본 구간 = 최근 1시간")
    @GetMapping("/sensors/history")
    public List<SensorReading> history(
            @Parameter(description = "센서 종류 (temperature | humidity | soil)", example = "temperature")
            @RequestParam String metric,
            @Parameter(description = "시작 시각 epoch seconds (생략 시 now-1h)")
            @RequestParam(required = false) Long from,
            @Parameter(description = "종료 시각 epoch seconds (생략 시 now)")
            @RequestParam(required = false) Long to) {
        long now = Instant.now().getEpochSecond();
        long fromSec = from != null ? from : now - 3600;
        long toSec = to != null ? to : now;
        return influxQueryService.history(metric, fromSec, toSec);
    }

    @Operation(summary = "실시간 센서 스트림 (SSE)",
            description = "Server-Sent Events 로 실시간 센서 이벤트 수신. event: sensor, data: JSON SensorReading")
    @GetMapping(value = "/sensors/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return sseBroadcastService.subscribe();
    }

    @Operation(summary = "디바이스 상태", description = "ESP32 online/offline 및 활성 SSE 구독자 수")
    @GetMapping("/device/status")
    public Map<String, Object> deviceStatus() {
        String status = Optional.ofNullable(redis.opsForValue().get(STATUS_KEY)).orElse("unknown");
        return Map.of(
                "status", status,
                "activeSubscribers", sseBroadcastService.getActiveSubscriberCount(),
                "timestamp", Instant.now().toString()
        );
    }
}
