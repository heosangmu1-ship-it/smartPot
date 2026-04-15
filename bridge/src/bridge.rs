//! MQTT 메시지를 받아서 Redis + InfluxDB 로 fan-out 하는 핵심 로직.

use crate::{config::Config, influx::InfluxWriter};
use chrono::Utc;
use redis::AsyncCommands;
use rumqttc::{AsyncClient, Event, EventLoop, MqttOptions, Packet, QoS};
use serde::Serialize;
use std::time::Duration;

/// SensorReading — Spring Boot 의 DTO 와 동일한 JSON 구조.
/// 이 구조로 Redis Pub/Sub 에 발행하면 Java 쪽 ObjectMapper 가 그대로 역직렬화 가능.
#[derive(Debug, Serialize)]
struct SensorReading<'a> {
    metric: &'a str,
    value: f64,
    timestamp: String, // ISO-8601
    #[serde(rename = "deviceId")]
    device_id: &'a str,
}

pub async fn run(cfg: Config) -> anyhow::Result<()> {
    // ── Redis 연결 (Multiplexed Connection Manager: 자동 재연결) ──
    let redis_client = redis::Client::open(cfg.redis_url.clone())?;
    let mut redis_conn = redis::aio::ConnectionManager::new(redis_client).await?;
    tracing::info!("Redis connected");

    // ── InfluxDB ──
    let influx = InfluxWriter::new(&cfg.influx_host, &cfg.influx_database, &cfg.influx_token);
    tracing::info!("InfluxDB client ready");

    // ── MQTT ──
    let (client, eventloop) = build_mqtt_client(&cfg);
    subscribe_topics(&client, &cfg.mqtt_topics).await?;

    // 이벤트 루프 실행 (무한)
    event_loop(cfg, eventloop, redis_conn, influx).await
}

fn build_mqtt_client(cfg: &Config) -> (AsyncClient, EventLoop) {
    // mqtt_url 형식: tcp://host:port
    let url = cfg.mqtt_url.trim_start_matches("tcp://");
    let (host, port) = match url.rsplit_once(':') {
        Some((h, p)) => (h.to_string(), p.parse::<u16>().unwrap_or(1883)),
        None => (url.to_string(), 1883),
    };

    let mut options = MqttOptions::new(&cfg.mqtt_client_id, host, port);
    options.set_keep_alive(Duration::from_secs(30));
    options.set_clean_session(true);

    AsyncClient::new(options, 20)
}

async fn subscribe_topics(client: &AsyncClient, topics: &[String]) -> anyhow::Result<()> {
    for topic in topics {
        client.subscribe(topic, QoS::AtMostOnce).await?;
        tracing::info!(topic = %topic, "Subscribed");
    }
    Ok(())
}

async fn event_loop(
    cfg: Config,
    mut eventloop: EventLoop,
    mut redis_conn: redis::aio::ConnectionManager,
    influx: InfluxWriter,
) -> anyhow::Result<()> {
    loop {
        let notification = eventloop.poll().await;
        match notification {
            Ok(Event::Incoming(Packet::Publish(publish))) => {
                let topic = publish.topic.clone();
                let payload = String::from_utf8_lossy(&publish.payload).trim().to_string();
                if let Err(e) =
                    handle_message(&cfg, &mut redis_conn, &influx, &topic, &payload).await
                {
                    tracing::warn!(error = %e, topic, "handle_message failed");
                }
            }
            Ok(Event::Incoming(Packet::ConnAck(_))) => {
                tracing::info!("MQTT connected");
            }
            Ok(_) => {}
            Err(e) => {
                tracing::warn!(error = %e, "MQTT event loop error, retrying in 2s");
                tokio::time::sleep(Duration::from_secs(2)).await;
            }
        }
    }
}

async fn handle_message(
    cfg: &Config,
    redis_conn: &mut redis::aio::ConnectionManager,
    influx: &InfluxWriter,
    topic: &str,
    payload: &str,
) -> anyhow::Result<()> {
    // 디바이스 상태
    if topic == "garden/status" {
        handle_status(cfg, redis_conn, payload).await?;
        return Ok(());
    }

    // 센서 값 파싱: garden/sensor/<metric>/state
    let parts: Vec<&str> = topic.split('/').collect();
    if parts.len() != 4 || parts[1] != "sensor" || parts[3] != "state" {
        return Ok(());
    }
    let metric = parts[2];

    let value: f64 = match payload.parse() {
        Ok(v) => v,
        Err(_) => {
            tracing::debug!(metric, payload, "non-numeric payload, skipped");
            return Ok(());
        }
    };

    let now = Utc::now();
    let now_iso = now.to_rfc3339();
    let now_nanos = now.timestamp_nanos_opt().unwrap_or(0);

    // (1) Redis Hash 현재값 캐시
    let _: () = redis_conn
        .hset(&cfg.current_hash_key, metric, value)
        .await?;
    let _: () = redis_conn
        .hset(&cfg.current_hash_key, "updated_at", &now_iso)
        .await?;

    // (2) Redis Pub/Sub fan-out
    let reading = SensorReading {
        metric,
        value,
        timestamp: now_iso.clone(),
        device_id: &cfg.device_id,
    };
    let json = serde_json::to_string(&reading)?;
    let _: () = redis_conn.publish(&cfg.pubsub_channel, &json).await?;

    // (3) InfluxDB 시계열 (실패해도 중단 안 함)
    if let Err(e) = influx.write(metric, &cfg.device_id, value, now_nanos).await {
        tracing::warn!(error = %e, metric, "influx write failed");
    }

    tracing::debug!(metric, value, "fan-out complete");
    Ok(())
}

async fn handle_status(
    cfg: &Config,
    redis_conn: &mut redis::aio::ConnectionManager,
    payload: &str,
) -> anyhow::Result<()> {
    let status_num = if payload.eq_ignore_ascii_case("online") { "1" } else { "0" };

    let _: () = redis_conn
        .hset(&cfg.current_hash_key, "status", payload)
        .await?;
    let _: () = redis_conn
        .hset(&cfg.current_hash_key, "status_num", status_num)
        .await?;

    // 상태 변경 이벤트도 발행
    let event = serde_json::json!({
        "metric": "status",
        "value": payload,
        "timestamp": Utc::now().to_rfc3339(),
        "deviceId": cfg.device_id,
    });
    let _: () = redis_conn
        .publish(&cfg.pubsub_channel, event.to_string())
        .await?;

    // offline 이면 오래된 센서 값 제거
    if payload.eq_ignore_ascii_case("offline") {
        let stale_fields = [
            "temperature",
            "humidity",
            "soil",
            "soil_raw",
            "wifi_signal",
            "wifi_percent",
            "uptime",
            "free_memory",
        ];
        for f in stale_fields {
            let _: () = redis_conn.hdel(&cfg.current_hash_key, f).await?;
        }
    }

    tracing::info!(status = payload, "device status updated");
    Ok(())
}
