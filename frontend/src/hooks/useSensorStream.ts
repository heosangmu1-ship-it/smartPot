import { useEffect, useRef, useState } from 'react';
import { API_BASE, type SensorReading } from '../api/client';

/**
 * SSE로 백엔드의 /api/sensors/stream을 구독.
 *
 * 두 종류의 이벤트를 수신:
 *  - "sensor"    : 매 2초 raw 센서 값 (카드 LIVE 표시)
 *  - "aggregate" : 매 1분 집계 평균값 (그래프 자동 갱신)
 */
export function useSensorStream() {
  const [latest, setLatest] = useState<Record<string, SensorReading>>({});
  const [aggregates, setAggregates] = useState<Record<string, SensorReading>>({});
  const [connected, setConnected] = useState(false);
  const eventSourceRef = useRef<EventSource | null>(null);

  useEffect(() => {
    const es = new EventSource(`${API_BASE}/api/sensors/stream`);
    eventSourceRef.current = es;

    es.addEventListener('connected', () => {
      setConnected(true);
    });

    es.addEventListener('sensor', (event) => {
      try {
        const reading: SensorReading = JSON.parse((event as MessageEvent).data);
        setLatest((prev) => ({ ...prev, [reading.metric]: reading }));
      } catch (e) {
        console.error('Failed to parse sensor SSE data', e);
      }
    });

    es.addEventListener('aggregate', (event) => {
      try {
        const reading: SensorReading = JSON.parse((event as MessageEvent).data);
        setAggregates((prev) => ({ ...prev, [reading.metric]: reading }));
      } catch (e) {
        console.error('Failed to parse aggregate SSE data', e);
      }
    });

    es.onerror = () => {
      setConnected(false);
    };

    return () => {
      es.close();
      eventSourceRef.current = null;
    };
  }, []);

  return { latest, aggregates, connected };
}
