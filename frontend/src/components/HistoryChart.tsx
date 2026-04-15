import { useEffect } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
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
  aggregate?: SensorReading;
}

/**
 * 시계열 그래프.
 *
 * 데이터 소스: InfluxDB 3 의 date_bin 으로 1분 단위 평균 집계된 데이터.
 * 실시간 값(SSE)은 카드(StatCard)에서만 보여주고, 차트는 안정적인
 * 집계 데이터로 추세를 표현해 노이즈를 제거함.
 */
export function HistoryChart({ metric, title, unit, color, aggregate }: Props) {
  const queryClient = useQueryClient();

  // 첫 로드는 REST API 로 InfluxDB 의 1분 평균 시리즈 fetch
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['history', metric],
    queryFn: () => fetchHistory(metric),
    staleTime: Infinity, // SSE 가 점진 갱신하므로 자동 refetch 불필요
  });

  // SSE 로 새 1분 집계가 도착하면 cache 에 append
  useEffect(() => {
    if (!aggregate || aggregate.metric !== metric) return;
    queryClient.setQueryData<SensorReading[]>(['history', metric], (prev) => {
      const list = prev ?? [];
      // 중복 timestamp 방지
      if (list.length > 0 && list[list.length - 1].timestamp === aggregate.timestamp) {
        return list;
      }
      // 1시간(60포인트) 윈도우 유지 — 오래된 점은 제거
      const next = [...list, aggregate];
      return next.length > 120 ? next.slice(next.length - 120) : next;
    });
  }, [aggregate, metric, queryClient]);

  const series = (data ?? []).map((r) => ({
    time: new Date(r.timestamp).getTime(),
    value: r.value,
    label: new Date(r.timestamp).toLocaleTimeString('ko-KR', {
      hour: '2-digit',
      minute: '2-digit',
    }),
  }));

  return (
    <div className="chart-card">
      <div className="chart-card__header">
        <div>
          <h3 className="chart-card__title">{title}</h3>
          <p className="chart-card__subtitle">1분 평균 · InfluxDB · SSE 자동 갱신</p>
        </div>
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
                domain={[
                  (dataMin: number) => Math.floor(dataMin - 1),
                  (dataMax: number) => Math.ceil(dataMax + 1),
                ]}
                allowDecimals={false}
              />
              <Tooltip
                contentStyle={{
                  background: '#1a1a2e',
                  border: `1px solid ${color}`,
                  borderRadius: 8,
                  color: '#fff',
                }}
                formatter={(value) => [
                  typeof value === 'number' ? `${value.toFixed(2)} ${unit}` : String(value),
                  title,
                ]}
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
