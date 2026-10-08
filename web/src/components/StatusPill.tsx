import { useTranslation } from "react-i18next";

const POSITIVE = new Set(["ACTIVE", "PUBLISHED", "WON", "SOLD", "RENTED"]);
const NEGATIVE = new Set(["DISABLED", "CLOSED", "ARCHIVED", "LOST", "EXPIRED", "SUSPENDED", "CHURNED"]);

/** Наскрізний візуальний мотив статусів — узгоджено з index.css .status-pill. */
export function StatusPill({ status }: { status: string }) {
  const { t } = useTranslation();
  const tone = POSITIVE.has(status) ? "status-positive" : NEGATIVE.has(status) ? "status-negative" : "";
  return <span className={`status-pill ${tone}`}>{t(`statuses.${status}`, { defaultValue: status })}</span>;
}
