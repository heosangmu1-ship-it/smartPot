//! Smart Garden Bridge
//!
//! 역할:
//!  - MQTT 브로커(Mosquitto)에서 `garden/#` 토픽을 구독
//!  - 센서 값을 파싱해서
//!      (1) Redis Hash `sensor:current` 에 현재값 저장
//!      (2) Redis Pub/Sub 채널 `sensor:update` 에 JSON 발행
//!      (3) InfluxDB 3 Core 에 시계열로 저장 (HTTP v3 write API)
//!  - 디바이스 status (online/offline) 추적
//!
//! 이 서비스는 Spring Boot 백엔드와 완전히 독립적이다.
//! 브리지가 멈춰도 REST/SSE API 는 Redis/InfluxDB 를 계속 읽을 수 있고,
//! Spring Boot 가 재시작돼도 브리지는 데이터 수집을 계속한다.

mod bridge;
mod config;
mod influx;

use tracing_subscriber::{fmt, prelude::*, EnvFilter};

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    let _ = dotenvy::dotenv();

    tracing_subscriber::registry()
        .with(EnvFilter::try_from_default_env().unwrap_or_else(|_| "garden_bridge=info,info".into()))
        .with(fmt::layer().compact())
        .init();

    let cfg = config::Config::from_env()?;
    tracing::info!(
        mqtt = %cfg.mqtt_url,
        redis = %cfg.redis_url,
        influx = %cfg.influx_host,
        "Starting garden-bridge"
    );

    bridge::run(cfg).await
}
