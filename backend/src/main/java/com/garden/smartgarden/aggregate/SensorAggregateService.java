package com.garden.smartgarden.aggregate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.garden.smartgarden.domain.SensorReading;
import com.garden.smartgarden.influx.InfluxQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 1분마다 InfluxDB에서 직전 1분 평균값을 조회해 SSE로 푸시.
 *
 * 장점:
 *  - 그래프가 새로고침 없이 자동 갱신
 *  - 차트는 노이즈가 제거된 안정적인 집계값 표시
 *  - 실시간 카드(SSE raw)와 차트(SSE aggregate)가 깔끔히 분리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensorAggregateService {

    public static final String AGGREGATE_CHANNEL = "sensor:aggregate";

    private static final List<String> METRICS = List.of("temperature", "humidity", "soil");

    private final InfluxQueryService influxQueryService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /**
     * 매분 0초에 직전 1분 동안의 평균값을 InfluxDB 에서 조회 후 Redis Pub/Sub 로 발행.
     * SSE 브로드캐스트 서비스가 같은 채널을 구독하고 있어 클라이언트로 push 됨.
     */
    @Scheduled(cron = "5 * * * * *") // 매분 5초 (전 분의 데이터 안정적으로 적재 후)
    public void publishMinuteAggregates() {
        long now = Instant.now().getEpochSecond();
        // 6분 범위로 조회하면 InfluxQueryService.pickBucketInterval 이 1분 평균을 적용
        long from = now - 6 * 60;

        for (String metric : METRICS) {
            try {
                List<SensorReading> readings = influxQueryService.history(metric, from, now);
                if (readings.isEmpty()) continue;

                // 가장 최근 버킷
                SensorReading latest = readings.get(readings.size() - 1);
                String json = objectMapper.writeValueAsString(latest);
                redis.convertAndSend(AGGREGATE_CHANNEL, json);
                log.debug("Published aggregate: {} = {}", metric, latest.getValue());
            } catch (Exception e) {
                log.error("Failed to publish aggregate for metric={}", metric, e);
            }
        }
    }
}
