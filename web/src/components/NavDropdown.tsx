import { useEffect, useRef, useState, type ReactNode } from "react";
import { NavLink, useLocation } from "react-router-dom";

interface NavItem {
  to: string;
  label: string;
}

export function NavDropdown({ label, items }: { label: string; items: NavItem[] }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const location = useLocation();
  const isActive = items.some((item) => location.pathname.startsWith(item.to));

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (ref.current && !ref.current.contains(event.target as Node)) setOpen(false);
    }
    function handleEscape(event: KeyboardEvent) {
      if (event.key === "Escape") setOpen(false);
    }
    document.addEventListener("mousedown", handleClickOutside);
    document.addEventListener("keydown", handleEscape);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleEscape);
    };
  }, []);

  return (
    <div className={`nav-group ${isActive ? "active" : ""}`} ref={ref}>
      <button className="nav-group-trigger" onClick={() => setOpen((v) => !v)} aria-expanded={open}>
        {label}
        <span className="chevron">▾</span>
      </button>
      {open && (
        <div className="nav-dropdown">
          {items.map((item) => (
            <NavLink key={item.to} to={item.to} onClick={() => setOpen(false)}>
              {item.label}
            </NavLink>
          ))}
        </div>
      )}
    </div>
  );
}

export function IconDropdown({
  icon,
  label,
  children,
}: {
  icon: ReactNode;
  label: string;
  children: (close: () => void) => ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (ref.current && !ref.current.contains(event.target as Node)) setOpen(false);
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  return (
    <div className="nav-group" ref={ref}>
      <button className="icon-button" onClick={() => setOpen((v) => !v)} aria-label={label} title={label}>
        {icon}
      </button>
      {open && <div className="nav-dropdown nav-dropdown-right">{children(() => setOpen(false))}</div>}
    </div>
  );
}
