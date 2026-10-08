import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "../../auth/AuthContext";
import { api } from "../../api/client";
import type { ShowingView } from "../../api/types";
import { PropertyTabs } from "../properties/PropertyTabs";
import { ShowingCard } from "./ShowingCard";
import { ScheduleShowingDialog } from "./ScheduleShowingDialog";

export function PropertyShowingsPage() {
  const { t } = useTranslation();
  const { token } = useAuth();
  const { propertyId } = useParams();
  const [showings, setShowings] = useState<ShowingView[] | null>(null);
  const [dialog, setDialog] = useState(false);

  const load = useCallback(() => {
    if (!token || !propertyId) return;
    api.get<ShowingView[]>(`/api/properties/${propertyId}/showings`, token).then(setShowings).catch(() => setShowings([]));
  }, [token, propertyId]);

  useEffect(() => load(), [load]);

  if (!token || !propertyId) return null;
  const upcoming = (showings ?? []).filter((s) => (s.status === "SCHEDULED" || s.status === "CONFIRMED")).sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt));
  const past = (showings ?? []).filter((s) => !(s.status === "SCHEDULED" || s.status === "CONFIRMED"));
  const completed = past.filter((s) => s.status === "COMPLETED" && s.interest);
  const avg = completed.length ? (completed.reduce((a, s) => a + (s.interest ?? 0), 0) / completed.length).toFixed(1) : null;

  return (
    <div className="page">
      <Link to="/properties" className="back-link">
        ← {t("propertyForm.backToList")}
      </Link>
      <PropertyTabs propertyId={propertyId} />
      <div className="page-header">
        <div>
          <h1>{t("showings.title")}</h1>
          {showings && showings.length > 0 && (
            <p className="page-subtitle">
              {t("showings.stats", { total: showings.length, completed: completed.length })}
              {avg && <> · {t("showings.avgInterest", { avg })}</>}
            </p>
          )}
        </div>
        <button type="button" onClick={() => setDialog(true)}>
          + {t("showings.schedule.button")}
        </button>
      </div>

      {showings && showings.length === 0 && (
        <div className="empty-state">
          <span className="empty-icon" aria-hidden="true">📅</span>
          <h2>{t("showings.emptyTitle")}</h2>
          <p className="hint">{t("showings.emptyHint")}</p>
        </div>
      )}

      {upcoming.length > 0 && <h2 className="section-title">{t("showings.upcoming")}</h2>}
      <div className="showing-list">
        {upcoming.map((s) => (
          <ShowingCard key={s.id} token={token} showing={s} hide="property" onChanged={(v) => setShowings((list) => (list ?? []).map((x) => (x.id === v.id ? v : x)))} />
        ))}
      </div>
      {past.length > 0 && <h2 className="section-title">{t("showings.past")}</h2>}
      <div className="showing-list">
        {past.map((s) => (
          <ShowingCard key={s.id} token={token} showing={s} hide="property" compact onChanged={(v) => setShowings((list) => (list ?? []).map((x) => (x.id === v.id ? v : x)))} />
        ))}
      </div>

      {dialog && <ScheduleShowingDialog token={token} propertyId={propertyId} onScheduled={() => { setDialog(false); load(); }} onClose={() => setDialog(false)} />}
    </div>
  );
}
