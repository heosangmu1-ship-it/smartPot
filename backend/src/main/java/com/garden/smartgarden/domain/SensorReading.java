package com.garden.smartgarden.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 센서에서 들어온 한 건의 측정값.
 * MQTT → Redis Pub/Sub → SSE 로 전파되는 이벤트의 공용 DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SensorReading {
    /** 센서 종류: temperature / humidity / soil */
    private String metric;

    /** 측정값 */
    private Double value;

    /** 측정 시각 (서버 수신 시각) */
    private Instant timestamp;

    /** 디바이스 식별자 (향후 여러 디바이스 확장 대비) */
    private String deviceId;
}
