import { useEffect, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useAuth } from "../auth/AuthContext";
import { api, ApiError } from "../api/client";
import type { CreateRoleRequest, PermissionCode, Role } from "../api/types";

const PERMISSION_CATEGORIES: { key: string; permissions: PermissionCode[] }[] = [
  { key: "property", permissions: ["PROPERTY_VIEW", "PROPERTY_CREATE", "PROPERTY_EDIT", "PROPERTY_DELETE"] },
  { key: "listings", permissions: ["LISTING_VIEW", "LISTING_PUBLISH", "LISTING_EDIT", "LISTING_CLOSE"] },
  { key: "inquiries", permissions: ["INQUIRY_VIEW", "INQUIRY_MANAGE"] },
  { key: "crm", permissions: ["LEAD_VIEW", "LEAD_MANAGE", "CLIENT_VIEW", "CLIENT_MANAGE"] },
  { key: "agents", permissions: ["AGENT_INVITE", "AGENT_MANAGE"] },
  { key: "roles", permissions: ["ROLE_MANAGE"] },
  { key: "tenantAdmin", permissions: ["TENANT_SETTINGS_MANAGE"] },
];

export function RolesPage() {
  const { t } = useTranslation();
  const { token } = useAuth();
  const [roles, setRoles] = useState<Role[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [selectedPermissions, setSelectedPermissions] = useState<Set<PermissionCode>>(new Set());

  async function loadRoles() {
    if (!token) return;
    try {
      setRoles(await api.get<Role[]>("/api/roles", token));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("roles.loadError"));
    }
  }

  useEffect(() => {
    loadRoles();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  function togglePermission(permission: PermissionCode) {
    setSelectedPermissions((prev) => {
      const next = new Set(prev);
      if (next.has(permission)) next.delete(permission);
      else next.add(permission);
      return next;
    });
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!token) return;
    setError(null);
    const form = new FormData(event.currentTarget);
    const request: CreateRoleRequest = {
      name: String(form.get("name")),
      permissions: Array.from(selectedPermissions),
    };
    try {
      await api.post("/api/roles", request, token);
      setShowForm(false);
      setSelectedPermissions(new Set());
      loadRoles();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("roles.createError"));
    }
  }

  async function deleteRole(role: Role) {
    if (!token) return;
    try {
      await api.del(`/api/roles/${role.id}`, token);
      loadRoles();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("roles.deleteError"));
    }
  }

  return (
    <div className="page">
      <div className="page-header">
        <h1>{t("roles.title")}</h1>
        <button onClick={() => setShowForm((v) => !v)}>
          {showForm ? t("roles.cancelButton") : t("roles.newButton")}
        </button>
      </div>

      {error && <p className="error">{error}</p>}

      {showForm && (
        <form onSubmit={handleSubmit} className="form">
          <label>
            {t("roles.form.name")}
            <input name="name" required />
          </label>
          {PERMISSION_CATEGORIES.map((category) => (
            <div key={category.key} className="permission-category">
              <p className="permission-category-title">{t(`roles.categories.${category.key}`)}</p>
              <div className="permission-grid">
                {category.permissions.map((permission) => (
                  <label key={permission} className="permission-checkbox">
                    <input
                      type="checkbox"
                      checked={selectedPermissions.has(permission)}
                      onChange={() => togglePermission(permission)}
                    />
                    {permission}
                  </label>
                ))}
              </div>
            </div>
          ))}
          <button type="submit">{t("roles.form.submit")}</button>
        </form>
      )}

      {roles.map((role) => (
        <div key={role.id} className="role-card">
          <div className="page-header">
            <h2>
              {role.name} {role.isSystemDefault && <span className="hint">{t("roles.builtIn")}</span>}
            </h2>
            {!role.isSystemDefault && (
              <button className="link-button" onClick={() => deleteRole(role)}>
                {t("roles.delete")}
              </button>
            )}
          </div>
          <div className="permission-tags">
            {role.permissions.map((p) => (
              <span key={p} className="permission-tag">
                {p}
              </span>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
