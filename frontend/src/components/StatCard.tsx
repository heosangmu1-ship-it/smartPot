import type { CSSProperties } from 'react';

interface Props {
  label: string;
  value: number | string | undefined;
  unit: string;
  color: string;
  icon?: string;
  live?: boolean;
}

export function StatCard({ label, value, unit, color, icon, live }: Props) {
  const formatted =
    value === undefined || value === null
      ? '—'
      : typeof value === 'number'
      ? value.toFixed(1)
      : value;

  const style: CSSProperties = {
    background: `linear-gradient(135deg, ${color}22, ${color}08)`,
    borderLeft: `4px solid ${color}`,
  };

  return (
    <div className="stat-card" style={style}>
      <div className="stat-card__header">
        <span className="stat-card__label">
          {icon && <span className="stat-card__icon">{icon}</span>}
          {label}
        </span>
        {live && <span className="stat-card__live">● LIVE</span>}
      </div>
      <div className="stat-card__value" style={{ color }}>
        {formatted}
        <span className="stat-card__unit">{unit}</span>
      </div>
    </div>
  );
}
