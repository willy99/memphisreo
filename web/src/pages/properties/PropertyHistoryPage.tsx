import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "../../auth/AuthContext";
import { api } from "../../api/client";
import type { TimelineEntry } from "../../api/types";
import { Timeline } from "../../components/Timeline";
import { PropertyTabs } from "./PropertyTabs";

export function PropertyHistoryPage() {
  const { t } = useTranslation();
  const { token } = useAuth();
  const { propertyId } = useParams();
  const [entries, setEntries] = useState<TimelineEntry[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token || !propertyId) return;
    api.get<TimelineEntry[]>(`/api/properties/${propertyId}/timeline`, token).then(setEntries).catch(() => setError(t("history.loadError")));
  }, [token, propertyId, t]);

  return (
    <div className="page">
      <Link to="/properties" className="back-link">
        ← {t("propertyForm.backToList")}
      </Link>
      <PropertyTabs propertyId={propertyId!} />
      <h1 className="section-title">{t("history.title")}</h1>
      {error && <p className="error">{error}</p>}
      {entries && <Timeline entries={entries} emptyText={t("history.empty")} />}
    </div>
  );
}
