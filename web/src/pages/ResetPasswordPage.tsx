import { useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { api, ApiError } from "../api/client";
import { AuthScreen } from "../components/AuthScreen";

const MIN_PASSWORD_LENGTH = 8;

export function ResetPasswordPage() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const tokenFromLink = params.get("token") ?? "";
  const [code, setCode] = useState(tokenFromLink);
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (password.length < MIN_PASSWORD_LENGTH) {
      setError(t("reset.tooShort", { min: MIN_PASSWORD_LENGTH }));
      return;
    }
    if (password !== confirm) {
      setError(t("reset.mismatch"));
      return;
    }
    setSubmitting(true);
    try {
      await api.post("/api/auth/password-reset/confirm", { token: code.trim(), newPassword: password });
      setDone(true);
    } catch (err) {
      setError(err instanceof ApiError && err.status === 403 ? t("reset.invalidCode") : t("reset.error"));
    } finally {
      setSubmitting(false);
    }
  }

  if (done) {
    return (
      <AuthScreen title={t("reset.doneTitle")}>
        <p className="auth-note">{t("reset.done")}</p>
        <Link to="/login" className="button-link button-block">
          {t("reset.toLogin")}
        </Link>
      </AuthScreen>
    );
  }

  return (
    <AuthScreen
      title={t("reset.title")}
      subtitle={t("reset.subtitle")}
      footer={
        <Link to="/login" className="auth-link">
          ← {t("forgot.backToLogin")}
        </Link>
      }
    >
      <form onSubmit={handleSubmit} className="form auth-form">
        {!tokenFromLink && (
          <label>
            {t("reset.code")}
            <input value={code} onChange={(e) => setCode(e.target.value)} required autoFocus className="mono-input" />
          </label>
        )}
        <label>
          {t("reset.password")}
          <input
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            autoFocus={!!tokenFromLink}
          />
        </label>
        <label>
          {t("reset.confirm")}
          <input
            type="password"
            autoComplete="new-password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            required
          />
        </label>
        <p className="hint">{t("reset.rule", { min: MIN_PASSWORD_LENGTH })}</p>
        {error && <p className="error">{error}</p>}
        <button type="submit" className="button-block" disabled={submitting}>
          {submitting ? t("reset.submitting") : t("reset.submit")}
        </button>
      </form>
    </AuthScreen>
  );
}
