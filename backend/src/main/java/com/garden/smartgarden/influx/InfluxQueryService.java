package com.garden.smartgarden.influx;

import com.garden.smartgarden.domain.SensorReading;
import com.influxdb.v3.client.InfluxDBClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * InfluxDB 3 Core 에서 시계열 데이터를 조회하는 서비스.
 * Flight SQL 로 쿼리를 보내고, Arrow 결과를 SensorReading 리스트로 변환.
 */
@Slf4j
@Service
public class InfluxQueryService {

    @Value("${influxdb.host}")
    private String host;

    @Value("${influxdb.database}")
    private String database;

    @Value("${influxdb.token:}")
    private String token;

    private InfluxDBClient client;

    @PostConstruct
    public void init() {
        String effectiveToken = token == null || token.isBlank() ? "unused" : token;
        client = InfluxDBClient.getInstance(host, effectiveToken.toCharArray(), database);
    }

    /**
     * 특정 메트릭의 과거 데이터를 조회.
     *
     * 시간 범위에 비해 데이터 밀도가 높을 경우 자동으로 시간 버킷팅(time bucketing)
     * 을 적용해 평균값으로 다운샘플링한다. 이 덕분에:
     *  - 그래프 렌더링 비용 감소 (1800 포인트 -> 120 포인트)
     *  - 센서 노이즈가 평탄화되어 추세가 더 잘 보임
     *
     * 버킷 크기 휴리스틱:
     *  - 10분 이하: 원본 그대로
     *  - 1시간:    30초 평균
     *  - 6시간:    3분 평균
     *  - 24시간:   10분 평균
     *  - 그 이상:  시간당 평균
     *
     * @param metric  temperature / humidity / soil
     * @param fromSec 조회 시작 (epoch seconds)
     * @param toSec   조회 끝 (epoch seconds)
     */
    public List<SensorReading> history(String metric, long fromSec, long toSec) {
        // metric 은 테이블명이므로 SQL injection 방어: 화이트리스트 검증
        if (!metric.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid metric name: " + metric);
        }

        long rangeSec = Math.max(1, toSec - fromSec);
        String bucketInterval = pickBucketInterval(rangeSec);

        String sql;
        if (bucketInterval == null) {
            // 짧은 범위는 원본 그대로
            sql = String.format(
                    "SELECT time, value FROM %s " +
                    "WHERE time >= '%s' AND time <= '%s' " +
                    "ORDER BY time",
                    metric,
                    Instant.ofEpochSecond(fromSec).toString(),
                    Instant.ofEpochSecond(toSec).toString()
            );
        } else {
            // DataFusion date_bin 으로 평균 집계
            sql = String.format(
                    "SELECT date_bin(INTERVAL '%s', time) AS time, AVG(value) AS value " +
                    "FROM %s " +
                    "WHERE time >= '%s' AND time <= '%s' " +
                    "GROUP BY 1 ORDER BY 1",
                    bucketInterval,
                    metric,
                    Instant.ofEpochSecond(fromSec).toString(),
                    Instant.ofEpochSecond(toSec).toString()
            );
        }

        List<SensorReading> result = new ArrayList<>();
        try (Stream<Object[]> rows = client.query(sql)) {
            rows.forEach(row -> {
                Instant time = toInstant(row[0]);
                Number value = (Number) row[1];
                result.add(SensorReading.builder()
                        .metric(metric)
                        .value(value.doubleValue())
                        .timestamp(time)
                        .build());
            });
        } catch (Exception e) {
            log.error("InfluxDB query failed: metric={}, from={}, to={}", metric, fromSec, toSec, e);
            throw new RuntimeException("InfluxDB query failed", e);
        }

        return result;
    }

    /**
     * 시간 범위에 따른 적절한 버킷 크기 선택.
     * null 을 반환하면 집계 없이 raw 데이터 반환.
     */
    private String pickBucketInterval(long rangeSec) {
        if (rangeSec <= 300) return null;             // 5분 이하: raw
        if (rangeSec <= 3600) return "1 minute";      // 1시간: 1분 평균 (60 포인트)
        if (rangeSec <= 6 * 3600) return "5 minutes"; // 6시간: 5분 평균 (72 포인트)
        if (rangeSec <= 86400) return "15 minutes";   // 24시간: 15분 평균 (96 포인트)
        return "1 hour";                              // 그 이상: 시간당 평균
    }

    /**
     * InfluxDB 3 Core는 time 컬럼을 BigInteger (epoch nanoseconds) 로 돌려주므로
     * Instant 로 변환해줘야 한다. 일부 경로에서는 Instant 로 오는 경우도 있어
     * 두 타입 모두 지원.
     */
    private Instant toInstant(Object value) {
        if (value instanceof Instant i) return i;
        if (value instanceof BigInteger bi) {
            long nanos = bi.longValueExact();
            long seconds = nanos / 1_000_000_000L;
            long nanoPart = nanos % 1_000_000_000L;
            return Instant.ofEpochSecond(seconds, nanoPart);
        }
        if (value instanceof Number n) {
            return Instant.ofEpochMilli(n.longValue());
        }
        throw new IllegalStateException("Unexpected time column type: " + value.getClass());
    }

    @PreDestroy
    public void close() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }
}
