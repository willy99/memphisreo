import { Link, NavLink, Navigate, Outlet, Route, Routes } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { AuthProvider, useAuth } from "./auth/AuthContext";
import { PlatformAuthProvider, usePlatformAuth } from "./auth/PlatformAuthContext";
import { ThemeProvider } from "./theme/ThemeContext";
import { LoginPage } from "./pages/LoginPage";
import { ForgotPasswordPage } from "./pages/ForgotPasswordPage";
import { ResetPasswordPage } from "./pages/ResetPasswordPage";
import { DashboardPage } from "./pages/DashboardPage";
import { PropertiesPage } from "./pages/PropertiesPage";
import { PropertyEditorPage } from "./pages/properties/PropertyEditorPage";
import { PropertySalePage } from "./pages/properties/PropertySalePage";
import { PropertyHistoryPage } from "./pages/properties/PropertyHistoryPage";
import { PublicLayout } from "./pages/public/PublicLayout";
import { PublicAgencyPage } from "./pages/public/PublicAgencyPage";
import { PublicPropertyPage } from "./pages/public/PublicPropertyPage";
import { OwnerPage } from "./pages/public/OwnerPage";
import { AgentsPage } from "./pages/AgentsPage";
import { ClientsPage } from "./pages/ClientsPage";
import { RolesPage } from "./pages/RolesPage";
import { AcceptInvitePage } from "./pages/AcceptInvitePage";
import { TenantSettingsPage } from "./pages/TenantSettingsPage";
import { ProfilePage } from "./pages/ProfilePage";
import { AdminLoginPage } from "./pages/admin/AdminLoginPage";
import { AdminTenantsPage } from "./pages/admin/AdminTenantsPage";
import { AdminTenantPage } from "./pages/admin/AdminTenantPage";
import { NavDropdown } from "./components/NavDropdown";
import { LanguageSwitcher } from "./components/LanguageSwitcher";
import { ThemeToggle } from "./components/ThemeToggle";
import { BrandMark } from "./components/AuthScreen";

/** Кабінет агенції: верхня панель + сторінки; без сесії — на логін. */
function AgencyLayout() {
  const { t } = useTranslation();
  const { token, logout } = useAuth();
  if (!token) {
    return <Navigate to="/login" replace />;
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <Link to="/dashboard" className="brand">
          <BrandMark />
          {t("app.name")}
        </Link>
        <nav className="nav-groups">
          <NavLink to="/dashboard" className="nav-link">
            {t("nav.dashboard")}
          </NavLink>
          <NavDropdown
            label={t("nav.workflow")}
            items={[{ to: "/properties", label: t("nav.properties") }]}
          />
          <NavDropdown
            label={t("nav.people")}
            items={[
              { to: "/clients", label: t("nav.clients") },
              { to: "/agents", label: t("nav.agents") },
            ]}
          />
          <NavDropdown
            label={t("nav.setup")}
            items={[
              { to: "/roles", label: t("nav.roles") },
              { to: "/settings", label: t("nav.agencySettings") },
            ]}
          />
        </nav>
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
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  );
}

/** Платформна адмінка: окремий realm і окремий токен. */
function AdminLayout() {
  const { t } = useTranslation();
  const { token, logout } = usePlatformAuth();
  if (!token) {
    return <Navigate to="/admin/login" replace />;
  }

  return (
    <div className="app-shell">
      <header className="topbar topbar-admin">
        <Link to="/admin" className="brand">
          <BrandMark />
          {t("app.name")}
          <span className="admin-badge">{t("admin.badge")}</span>
        </Link>
        <nav className="nav-groups">
          <NavLink to="/admin" end className="nav-link">
            {t("admin.tenantsTitle")}
          </NavLink>
        </nav>
        <div className="icon-cluster">
          <LanguageSwitcher />
          <ThemeToggle />
          <button className="icon-button" onClick={logout} title={t("nav.logout")} aria-label={t("nav.logout")}>
            ⏻
          </button>
        </div>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  );
}

function AppRoutes() {
  const { token } = useAuth();

  return (
    <Routes>
      <Route path="/login" element={token ? <Navigate to="/dashboard" replace /> : <LoginPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route path="/accept-invite" element={<AcceptInvitePage />} />
      <Route path="/admin/login" element={<AdminLoginPage />} />
      <Route path="/owner/:token" element={<OwnerPage />} />
      <Route path="/p/:slug" element={<PublicLayout />}>
        <Route index element={<PublicAgencyPage />} />
        <Route path=":propertyId" element={<PublicPropertyPage />} />
      </Route>

      <Route path="/admin" element={<AdminLayout />}>
        <Route index element={<AdminTenantsPage />} />
        <Route path="tenants/:tenantId" element={<AdminTenantPage />} />
      </Route>

      <Route element={<AgencyLayout />}>
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/properties" element={<PropertiesPage />} />
        <Route path="/properties/new" element={<PropertyEditorPage key="new" />} />
        <Route path="/properties/:propertyId" element={<PropertyEditorPage />} />
        <Route path="/properties/:propertyId/sale" element={<PropertySalePage />} />
        <Route path="/properties/:propertyId/history" element={<PropertyHistoryPage />} />
        <Route path="/agents" element={<AgentsPage />} />
        <Route path="/clients" element={<ClientsPage />} />
        <Route path="/roles" element={<RolesPage />} />
        <Route path="/settings" element={<TenantSettingsPage />} />
        <Route path="/profile" element={<ProfilePage />} />
      </Route>

      <Route path="*" element={<Navigate to={token ? "/dashboard" : "/login"} replace />} />
    </Routes>
  );
}

function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <PlatformAuthProvider>
          <AppRoutes />
        </PlatformAuthProvider>
      </AuthProvider>
    </ThemeProvider>
  );
}

export default App;
