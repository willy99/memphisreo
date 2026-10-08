import { NavLink } from "react-router-dom";
import { useTranslation } from "react-i18next";

/** Вкладки картки об'єкта: Огляд (редактор) · Продаж · Історія. Покази/Офери/Угода — далі. */
export function PropertyTabs({ propertyId }: { propertyId: string }) {
  const { t } = useTranslation();
  const tabs = [
    { to: `/properties/${propertyId}`, label: t("propertyTabs.overview"), end: true },
    { to: `/properties/${propertyId}/sale`, label: t("propertyTabs.sale"), end: false },
    { to: `/properties/${propertyId}/history`, label: t("propertyTabs.history"), end: false },
  ];
  return (
    <nav className="property-tabs" aria-label={t("propertyTabs.label")}>
      {tabs.map((tab) => (
        <NavLink key={tab.to} to={tab.to} end={tab.end} className={({ isActive }) => `property-tab${isActive ? " active" : ""}`}>
          {tab.label}
        </NavLink>
      ))}
    </nav>
  );
}
