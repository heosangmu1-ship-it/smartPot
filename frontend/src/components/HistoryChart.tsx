import { useQuery } from '@tanstack/react-query';
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { fetchHistory, type SensorReading } from '../api/client';

interface Props {
  metric: string;
  title: string;
  unit: string;
  color: string;
  liveValue?: SensorReading;
}

export function HistoryChart({ metric, title, unit, color, liveValue }: Props) {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['history', metric],
    queryFn: () => fetchHistory(metric),
    refetchInterval: 30_000, // 30초마다 백엔드에서 이력 재조회
    staleTime: 10_000,
  });

  // 실시간 값이 들어오면 차트에 추가
  const series = (data ?? []).concat(liveValue ? [liveValue] : []).map((r) => ({
    time: new Date(r.timestamp).getTime(),
    value: r.value,
    label: new Date(r.timestamp).toLocaleTimeString('ko-KR', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    }),
  }));

  return (
    <div className="chart-card">
      <div className="chart-card__header">
        <h3 className="chart-card__title">{title}</h3>
        {isLoading && <span className="chart-card__hint">Loading...</span>}
        {isError && (
          <button onClick={() => refetch()} className="chart-card__retry">
            재시도
          </button>
        )}
      </div>
      <div className="chart-card__body">
        {series.length === 0 && !isLoading ? (
          <div className="chart-card__empty">데이터 없음</div>
        ) : (
          <ResponsiveContainer width="100%" height={260}>
            <LineChart data={series}>
              <defs>
                <linearGradient id={`grad-${metric}`} x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor={color} stopOpacity={0.4} />
                  <stop offset="100%" stopColor={color} stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#333" opacity={0.3} />
              <XAxis
                dataKey="label"
                stroke="#888"
                fontSize={11}
                interval="preserveStartEnd"
                minTickGap={50}
              />
              <YAxis
                stroke="#888"
                fontSize={11}
                unit={unit}
                domain={['auto', 'auto']}
              />
              <Tooltip
                contentStyle={{
                  background: '#1a1a2e',
                  border: `1px solid ${color}`,
                  borderRadius: 8,
                  color: '#fff',
                }}
                formatter={(value: number) => [`${value.toFixed(2)} ${unit}`, title]}
              />
              <Line
                type="monotone"
                dataKey="value"
                stroke={color}
                strokeWidth={2.5}
                dot={false}
                activeDot={{ r: 5 }}
              />
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>
    </div>
  );
}
