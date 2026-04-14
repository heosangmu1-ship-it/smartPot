import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
import { fetchCurrent, fetchDeviceStatus } from './api/client';
import { StatCard } from './components/StatCard';
import { HistoryChart } from './components/HistoryChart';
import { useSensorStream } from './hooks/useSensorStream';
import './App.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
});

function Dashboard() {
  // 1) 페이지 로드 시 현재값 즉시 조회 (Redis에서)
  const { data: current } = useQuery({
    queryKey: ['current'],
    queryFn: fetchCurrent,
    refetchInterval: 10_000,
  });

  // 2) 디바이스 상태
  const { data: status } = useQuery({
    queryKey: ['status'],
    queryFn: fetchDeviceStatus,
    refetchInterval: 5_000,
  });

  // 3) SSE로 실시간 업데이트 수신
  const { latest: liveSensors, connected: sseConnected } = useSensorStream();

  // 실시간 값이 있으면 그걸 쓰고, 없으면 초기 REST 값 사용
  const temperature = liveSensors.temperature?.value ?? current?.temperature;
  const humidity = liveSensors.humidity?.value ?? current?.humidity;

  const isOnline = (status?.status ?? current?.status) === 'online';

  return (
    <div className="dashboard">
      <header className="dashboard__header">
        <div className="dashboard__title">
          <span className="dashboard__icon">🌱</span>
          <div>
            <h1>Smart Garden Monitor</h1>
            <p className="dashboard__subtitle">
              ESP32 기반 스마트팜 실시간 모니터링 시스템
            </p>
          </div>
        </div>
        <div className="dashboard__indicators">
          <div className={`indicator ${isOnline ? 'indicator--on' : 'indicator--off'}`}>
            <span className="indicator__dot" />
            Device {isOnline ? 'ONLINE' : 'OFFLINE'}
          </div>
          <div className={`indicator ${sseConnected ? 'indicator--on' : 'indicator--off'}`}>
            <span className="indicator__dot" />
            SSE {sseConnected ? 'Connected' : 'Disconnected'}
          </div>
        </div>
      </header>

      <section className="stats-grid">
        <StatCard
          label="온도"
          value={temperature}
          unit="°C"
          color="#ff8b3d"
          icon="🌡"
          live={!!liveSensors.temperature}
        />
        <StatCard
          label="습도"
          value={humidity}
          unit="%"
          color="#4dabf7"
          icon="💧"
          live={!!liveSensors.humidity}
        />
        <StatCard
          label="활성 구독자"
          value={status?.activeSubscribers ?? 0}
          unit=""
          color="#51cf66"
          icon="🔌"
        />
      </section>

      <section className="charts-grid">
        <HistoryChart
          metric="temperature"
          title="온도 이력 (1시간)"
          unit="°C"
          color="#ff8b3d"
          liveValue={liveSensors.temperature}
        />
        <HistoryChart
          metric="humidity"
          title="습도 이력 (1시간)"
          unit="%"
          color="#4dabf7"
          liveValue={liveSensors.humidity}
        />
      </section>

      <footer className="dashboard__footer">
        <span>ESP32-S3 · MQTT · Spring Boot · Redis · InfluxDB 3 · React 18</span>
      </footer>
    </div>
  );
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <Dashboard />
    </QueryClientProvider>
  );
}
