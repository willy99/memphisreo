import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { api } from "../api/client";
import { AuthScreen } from "../components/AuthScreen";

export function ForgotPasswordPage() {
  const { t } = useTranslation();
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await api.post("/api/auth/password-reset/request", { email });
      setSent(true);
    } catch {
      setError(t("forgot.error"));
    } finally {
      setSubmitting(false);
    }
  }

  const backToLogin = (
    <Link to="/login" className="auth-link">
      ← {t("forgot.backToLogin")}
    </Link>
  );

  if (sent) {
    return (
      <AuthScreen title={t("forgot.sentTitle")} footer={backToLogin}>
        <p className="auth-note">{t("forgot.sent", { email })}</p>
        <Link to="/reset-password" className="auth-link">
          {t("forgot.haveCode")}
        </Link>
      </AuthScreen>
    );
  }

  return (
    <AuthScreen title={t("forgot.title")} subtitle={t("forgot.subtitle")} footer={backToLogin}>
      <form onSubmit={handleSubmit} className="form auth-form">
        <label>
          {t("login.email")}
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus />
        </label>
        {error && <p className="error">{error}</p>}
        <button type="submit" className="button-block" disabled={submitting}>
          {submitting ? t("forgot.submitting") : t("forgot.submit")}
        </button>
      </form>
    </AuthScreen>
  );
}
