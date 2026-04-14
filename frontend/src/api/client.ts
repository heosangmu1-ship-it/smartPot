import axios from 'axios';

export const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080';

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
