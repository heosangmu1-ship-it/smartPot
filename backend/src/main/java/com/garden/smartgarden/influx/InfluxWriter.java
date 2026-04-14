package com.garden.smartgarden.influx;

import com.garden.smartgarden.domain.SensorReading;
import com.influxdb.v3.client.InfluxDBClient;
import com.influxdb.v3.client.Point;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * InfluxDB 3 Core 에 센서 이력을 쓰는 서비스.
 * MQTT 핸들러가 writeAsync() 로 호출하면 별도 스레드에서 실제 write.
 */
@Slf4j
@Component
public class InfluxWriter {

    @Value("${influxdb.host}")
    private String host;

    @Value("${influxdb.database}")
    private String database;

    @Value("${influxdb.token:}")
    private String token;

    private InfluxDBClient client;

    @PostConstruct
    public void init() {
        // InfluxDB 3 Core 는 --without-auth 로 뜨면 token 불필요하지만
        // 클라이언트 API 가 token 파라미터를 요구하므로 빈 문자열 허용용 더미 값.
        String effectiveToken = token == null || token.isBlank() ? "unused" : token;
        client = InfluxDBClient.getInstance(host, effectiveToken.toCharArray(), database);
        log.info("InfluxDB client initialized: host={}, db={}", host, database);
    }

    @Async
    public void writeAsync(SensorReading reading) {
        try {
            var point = Point.measurement(reading.getMetric())
                    .setField("value", reading.getValue())
                    .setTag("device_id", reading.getDeviceId())
                    .setTimestamp(reading.getTimestamp());
            client.writePoint(point);
        } catch (Exception e) {
            log.error("Failed to write to InfluxDB", e);
        }
    }

    @PreDestroy
    public void close() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("Error closing InfluxDB client", e);
            }
        }
    }
}
