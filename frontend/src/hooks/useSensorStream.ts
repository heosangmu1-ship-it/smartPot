import { useEffect, useRef, useState } from 'react';
import { API_BASE, type SensorReading } from '../api/client';

/**
 * SSE로 백엔드의 /api/sensors/stream을 구독해서
 * 실시간 센서 이벤트를 수신.
 */
export function useSensorStream() {
  const [latest, setLatest] = useState<Record<string, SensorReading>>({});
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
        console.error('Failed to parse SSE data', e);
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

  return { latest, connected };
}
