const POSITIVE = new Set(["ACTIVE", "PUBLISHED", "WON", "SOLD", "RENTED"]);
const NEGATIVE = new Set(["DISABLED", "CLOSED", "ARCHIVED", "LOST", "EXPIRED"]);

/** Наскрізний візуальний мотив статусів — узгоджено з index.css .status-pill. */
export function StatusPill({ status }: { status: string }) {
  const tone = POSITIVE.has(status) ? "status-positive" : NEGATIVE.has(status) ? "status-negative" : "";
  return <span className={`status-pill ${tone}`}>{status}</span>;
}
