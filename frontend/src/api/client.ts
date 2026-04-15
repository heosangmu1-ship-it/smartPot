import axios from 'axios';

/**
 * Base URL 전략:
 *  - dev 모드: 빈 문자열 → 브라우저가 Vite dev 서버(5173) 에 요청 →
 *    vite.config.ts 의 proxy 가 /api 를 localhost:8080 으로 포워딩
 *  - 프로덕션: 빈 문자열 → Spring Boot 가 static + REST 를 같이 서빙하므로 동일 오리진
 * 둘 다 상대 경로 하나로 통일.
 */
export const API_BASE = import.meta.env.VITE_API_BASE ?? '';

export const apiClient = axios.create({
  baseURL: API_BASE,
  timeout: 5000,
});

export interface CurrentSensors {
  temperature?: number;
  humidity?: number;
  soil?: number;
  status: string;
  timestamp: string;
}

export interface SensorReading {
  metric: string;
  value: number;
  timestamp: string;
  deviceId?: string;
}

export interface DeviceStatus {
  status: string;
  activeSubscribers: number;
  timestamp: string;
}

export async function fetchCurrent(): Promise<CurrentSensors> {
  const { data } = await apiClient.get<CurrentSensors>('/api/sensors/current');
  return data;
}

export async function fetchHistory(
  metric: string,
  fromSec?: number,
  toSec?: number
): Promise<SensorReading[]> {
  const { data } = await apiClient.get<SensorReading[]>('/api/sensors/history', {
    params: { metric, from: fromSec, to: toSec },
  });
  return data;
}

export async function fetchDeviceStatus(): Promise<DeviceStatus> {
  const { data } = await apiClient.get<DeviceStatus>('/api/device/status');
  return data;
}
