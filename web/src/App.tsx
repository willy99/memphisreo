import { Navigate, NavLink, Route, Routes } from "react-router-dom";
import { AuthProvider, useAuth } from "./auth/AuthContext";
import { LoginPage } from "./pages/LoginPage";
import { PropertiesPage } from "./pages/PropertiesPage";
import { ListingsPage } from "./pages/ListingsPage";

function RequireAuth({ children }: { children: React.ReactNode }) {
  const { token } = useAuth();
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}

function Layout() {
  const { token, logout } = useAuth();
  return (
    <div className="app-shell">
      <nav className="nav">
        <span className="brand">Memphisreo</span>
        {token && (
          <>
            <NavLink to="/properties">Об'єкти</NavLink>
            <NavLink to="/listings">Лістинги</NavLink>
            <button className="link-button" onClick={logout}>
              Вийти
            </button>
          </>
        )}
      </nav>
      <main>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
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
          <Route path="*" element={<Navigate to={token ? "/properties" : "/login"} replace />} />
        </Routes>
      </main>
    </div>
  );
}

function App() {
  return (
    <AuthProvider>
      <Layout />
    </AuthProvider>
  );
}

export default App;
