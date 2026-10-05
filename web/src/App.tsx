import { Link, Navigate, Route, Routes } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { AuthProvider, useAuth } from "./auth/AuthContext";
import { ThemeProvider } from "./theme/ThemeContext";
import { LoginPage } from "./pages/LoginPage";
import { PropertiesPage } from "./pages/PropertiesPage";
import { ListingsPage } from "./pages/ListingsPage";
import { AgentsPage } from "./pages/AgentsPage";
import { RolesPage } from "./pages/RolesPage";
import { AcceptInvitePage } from "./pages/AcceptInvitePage";
import { TenantSettingsPage } from "./pages/TenantSettingsPage";
import { ProfilePage } from "./pages/ProfilePage";
import { NavDropdown } from "./components/NavDropdown";
import { LanguageSwitcher } from "./components/LanguageSwitcher";
import { ThemeToggle } from "./components/ThemeToggle";

function RequireAuth({ children }: { children: React.ReactNode }) {
  const { token } = useAuth();
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}

function Layout() {
  const { t } = useTranslation();
  const { token, logout } = useAuth();

  return (
    <div className="app-shell">
      <header className="topbar">
        <span className="brand">{t("app.name")}</span>
        {token && (
          <nav className="nav-groups">
            <NavDropdown
              label={t("nav.workflow")}
              items={[
                { to: "/properties", label: t("nav.properties") },
                { to: "/listings", label: t("nav.listings") },
              ]}
            />
            <NavDropdown label={t("nav.people")} items={[{ to: "/agents", label: t("nav.agents") }]} />
            <NavDropdown
              label={t("nav.setup")}
              items={[
                { to: "/roles", label: t("nav.roles") },
                { to: "/settings", label: t("nav.agencySettings") },
              ]}
            />
          </nav>
        )}
        {token && (
          <div className="icon-cluster">
            <LanguageSwitcher />
            <ThemeToggle />
            <Link to="/profile" className="icon-button" title={t("nav.profile")} aria-label={t("nav.profile")}>
              👤
            </Link>
            <button className="icon-button" onClick={logout} title={t("nav.logout")} aria-label={t("nav.logout")}>
              ⏻
            </button>
          </div>
        )}
      </header>
      <main>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/accept-invite" element={<AcceptInvitePage />} />
          <Route
            path="/properties"
            element={
              <RequireAuth>
                <PropertiesPage />
              </RequireAuth>
            }
          />
          <Route
            path="/listings"
            element={
              <RequireAuth>
                <ListingsPage />
              </RequireAuth>
            }
          />
          <Route
            path="/agents"
            element={
              <RequireAuth>
                <AgentsPage />
              </RequireAuth>
            }
          />
          <Route
            path="/roles"
            element={
              <RequireAuth>
                <RolesPage />
              </RequireAuth>
            }
          />
          <Route
            path="/settings"
            element={
              <RequireAuth>
                <TenantSettingsPage />
              </RequireAuth>
            }
          />
          <Route
            path="/profile"
            element={
              <RequireAuth>
                <ProfilePage />
              </RequireAuth>
            }
          />
          <Route path="*" element={<Navigate to={token ? "/properties" : "/login"} replace />} />
        </Routes>
      </main>
    </div>
  );
}

function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <Layout />
      </AuthProvider>
    </ThemeProvider>
  );
}

export default App;
