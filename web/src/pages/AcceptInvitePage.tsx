import { useState, type FormEvent } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { api, ApiError } from "../api/client";
import type { AcceptInviteRequest } from "../api/types";
import { AuthScreen } from "../components/AuthScreen";

export function AcceptInvitePage() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token") ?? "";
  const navigate = useNavigate();
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const request: AcceptInviteRequest = { token, password };
      await api.post<void>("/api/auth/accept-invite", request);
      navigate("/login");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("acceptInvite.error"));
    } finally {
      setSubmitting(false);
    }
  }

  if (!token) {
    return (
      <AuthScreen title={t("acceptInvite.title")}>
        <p className="error">{t("acceptInvite.missingToken")}</p>
      </AuthScreen>
    );
  }

  return (
    <AuthScreen title={t("acceptInvite.title")}>
      <form onSubmit={handleSubmit} className="form auth-form">
        <label>
          {t("acceptInvite.password")}
          <input
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={8}
            autoFocus
          />
        </label>
        {error && <p className="error">{error}</p>}
        <button type="submit" className="button-block" disabled={submitting}>
          {submitting ? t("acceptInvite.submitting") : t("acceptInvite.submit")}
        </button>
      </form>
    </AuthScreen>
  );
}
