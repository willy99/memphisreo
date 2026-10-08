import type { ReactNode } from "react";

export function StatCard({ label, value, icon }: { label: string; value: number | null; icon: ReactNode }) {
  return (
    <div className="stat-card">
      <span className="stat-icon" aria-hidden="true">
        {icon}
      </span>
      <div>
        <div className="stat-value">{value === null ? "—" : value.toLocaleString()}</div>
        <div className="stat-label">{label}</div>
      </div>
    </div>
  );
}

export const AgentsIcon = (
  <svg viewBox="0 0 24 24" width="20" height="20">
    <path
      d="M16 11a4 4 0 1 0-4-4 4 4 0 0 0 4 4zm-8 1a3 3 0 1 0-3-3 3 3 0 0 0 3 3zm8 1c-2.7 0-8 1.3-8 4v2h16v-2c0-2.7-5.3-4-8-4zm-8 0c-.3 0-.7 0-1.1.1A4.9 4.9 0 0 1 9 17v2H2v-2c0-2.2 4.2-4 6-4z"
      fill="currentColor"
    />
  </svg>
);

export const PropertiesIcon = (
  <svg viewBox="0 0 24 24" width="20" height="20">
    <path d="M3 11.2 12 4l9 7.2V20a1 1 0 0 1-1 1h-5.5v-6h-5v6H4a1 1 0 0 1-1-1z" fill="currentColor" />
  </svg>
);
