//! 환경변수 기반 설정 로더.

use std::env;

#[derive(Debug, Clone)]
pub struct Config {
    pub mqtt_url: String,      // tcp://host:port
    pub mqtt_client_id: String,
    pub mqtt_topics: Vec<String>,

    pub redis_url: String,     // redis://host:port

    pub influx_host: String,   // http://host:port
    pub influx_database: String,
    pub influx_token: String,  // 없으면 빈 문자열

    pub pubsub_channel: String,
    pub current_hash_key: String,
    pub device_id: String,
}

impl Config {
    pub fn from_env() -> anyhow::Result<Self> {
        let mqtt_topics = env::var("MQTT_TOPICS")
            .unwrap_or_else(|_| "garden/sensor/+/state,garden/status".into())
            .split(',')
            .map(|s| s.trim().to_string())
            .collect();

        Ok(Self {
            mqtt_url: env::var("MQTT_URL").unwrap_or_else(|_| "tcp://127.0.0.1:1883".into()),
            mqtt_client_id: env::var("MQTT_CLIENT_ID").unwrap_or_else(|_| "garden-bridge".into()),
            mqtt_topics,
            redis_url: env::var("REDIS_URL").unwrap_or_else(|_| "redis://127.0.0.1:6379".into()),
            influx_host: env::var("INFLUX_HOST").unwrap_or_else(|_| "http://127.0.0.1:8181".into()),
            influx_database: env::var("INFLUX_DATABASE").unwrap_or_else(|_| "garden_sensors".into()),
            influx_token: env::var("INFLUX_TOKEN").unwrap_or_default(),
            pubsub_channel: env::var("REDIS_PUBSUB_CHANNEL").unwrap_or_else(|_| "sensor:update".into()),
            current_hash_key: "sensor:current".into(),
            device_id: env::var("DEVICE_ID").unwrap_or_else(|_| "garden-sensor-01".into()),
        })
    }
}
