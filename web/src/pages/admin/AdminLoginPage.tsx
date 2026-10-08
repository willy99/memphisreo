import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { usePlatformAuth } from "../../auth/PlatformAuthContext";
import { ApiError } from "../../api/client";
import { AuthScreen } from "../../components/AuthScreen";

export function AdminLoginPage() {
  const { t } = useTranslation();
  const { login } = usePlatformAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
      navigate("/admin");
    } catch (err) {
      setError(err instanceof ApiError && err.status === 403 ? t("login.invalid") : t("login.error"));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthScreen title={t("admin.loginTitle")} subtitle={t("admin.loginSubtitle")} badge={t("admin.badge")}>
      <form onSubmit={handleSubmit} className="form auth-form">
        <label>
          {t("login.email")}
          <input
            type="email"
            autoComplete="username"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoFocus
          />
        </label>
        <label>
          {t("login.password")}
          <input
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>
        {error && <p className="error">{error}</p>}
        <button type="submit" className="button-block" disabled={submitting}>
          {submitting ? t("login.submitting") : t("login.submit")}
        </button>
      </form>
    </AuthScreen>
  );
}
