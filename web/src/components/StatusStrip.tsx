import type { ShowingStatus } from "../api/types";

interface Step {
  key: string;
  label: string;
}

/**
 * Стрічка статусів: кроки один за одним, пройдені — підсвічені, наступні — сірі.
 * Скасовано / не прийшли — вся стрічка пригашена й перекреслена, з підписом.
 */
export function StatusStrip({ steps, current, terminated, terminatedLabel, compact }: { steps: Step[]; current: number; terminated?: boolean; terminatedLabel?: string; compact?: boolean }) {
  return (
    <div className={`status-strip${terminated ? " terminated" : ""}${compact ? " compact" : ""}`} role="list" aria-label={terminatedLabel ?? steps[Math.min(current, steps.length - 1)]?.label}>
      {steps.map((step, i) => (
        <div key={step.key} role="listitem" className={`strip-step${i < current ? " done" : ""}${i === current && !terminated ? " current" : ""}`} aria-current={i === current ? "step" : undefined}>
          <span className="strip-bar" />
          <span className="strip-label">{step.label}</span>
        </div>
      ))}
      {terminated && terminatedLabel && <span className="strip-terminated">{terminatedLabel}</span>}
    </div>
  );
}

/** Кроки показу: Заплановано → Підтверджено → Відбувся → Фідбек. */
export function showingSteps(t: (k: string) => string): Step[] {
  return [
    { key: "SCHEDULED", label: t("showings.steps.SCHEDULED") },
    { key: "CONFIRMED", label: t("showings.steps.CONFIRMED") },
    { key: "COMPLETED", label: t("showings.steps.COMPLETED") },
    { key: "FEEDBACK", label: t("showings.steps.FEEDBACK") },
  ];
}

export function showingProgress(status: ShowingStatus, hasFeedback: boolean): { current: number; terminated: boolean } {
  switch (status) {
    case "SCHEDULED":
      return { current: 1, terminated: false };
    case "CONFIRMED":
      return { current: 2, terminated: false };
    case "COMPLETED":
      return { current: hasFeedback ? 4 : 3, terminated: false };
    default:
      return { current: 2, terminated: true };
  }
}
