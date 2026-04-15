//! InfluxDB 3 Core HTTP v3 write API 클라이언트.
//!
//! POST /api/v3/write_lp?db=garden_sensors
//! body: Line Protocol (예: `temperature,device_id=garden-sensor-01 value=24.5 <ns>`)
//!
//! 장점: Flight SQL 안 써도 되니까 gRPC 의존성 없이 순수 HTTP 한 줄로 끝.

use reqwest::Client;

pub struct InfluxWriter {
    client: Client,
    write_url: String,
    token: String,
}

impl InfluxWriter {
    pub fn new(host: &str, database: &str, token: &str) -> Self {
        // write_lp 엔드포인트에 database 쿼리스트링으로 붙이고 precision=ns
        let write_url = format!(
            "{}/api/v3/write_lp?db={}&precision=nanosecond",
            host.trim_end_matches('/'),
            database
        );
        Self {
            client: Client::builder()
                .timeout(std::time::Duration::from_secs(3))
                .build()
                .expect("build reqwest client"),
            write_url,
            token: token.to_string(),
        }
    }

    /// 단일 포인트 쓰기. Line Protocol 로 직접 조합.
    pub async fn write(
        &self,
        measurement: &str,
        device_id: &str,
        value: f64,
        ts_nanos: i64,
    ) -> anyhow::Result<()> {
        // tag value 는 공백/쉼표를 escape 해야 하지만, 우리는 ASCII only 라 생략
        let line = format!(
            "{},device_id={} value={} {}",
            measurement, device_id, value, ts_nanos
        );

        let mut req = self.client.post(&self.write_url).body(line);

        if !self.token.is_empty() {
            req = req.bearer_auth(&self.token);
        }

        let resp = req.send().await?;
        let status = resp.status();
        if !status.is_success() {
            let body = resp.text().await.unwrap_or_default();
            anyhow::bail!("InfluxDB write failed: {} {}", status, body);
        }
        Ok(())
    }
}
