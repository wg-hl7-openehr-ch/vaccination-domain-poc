// Shared UI components for Impfdossier CH

const { useState, useEffect, useRef, useMemo } = React;

/* ---------------- Icons (inline SVG) ---------------- */

const Icon = {
  Search: (p) =>
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}>
      <circle cx="11" cy="11" r="7" />
      <path d="m21 21-4.3-4.3" />
    </svg>,

  Plus: (p) =>
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}>
      <path d="M12 5v14M5 12h14" />
    </svg>,

  Back: (p) =>
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}>
      <path d="m15 18-6-6 6-6" />
    </svg>,

  Chevron: (p) =>
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}>
      <path d="m9 18 6-6-6-6" />
    </svg>,

  ChevronDown: (p) =>
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}>
      <path d="m6 9 6 6 6-6" />
    </svg>,

  Filter: (p) =>
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}>
      <path d="M3 6h18M6 12h12M10 18h4" />
    </svg>,

  Mail: (p) =>
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" {...p}>
      <rect x="3" y="5" width="18" height="14" rx="2" />
      <path d="m3 7 9 6 9-6" />
    </svg>,

  Phone: (p) =>
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" {...p}>
      <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" />
    </svg>,

  Pin: (p) =>
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" {...p}>
      <path d="M20 10c0 7-8 12-8 12s-8-5-8-12a8 8 0 1 1 16 0z" />
      <circle cx="12" cy="10" r="3" />
    </svg>,

  Cake: (p) =>
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" {...p}>
      <path d="M20 21V11a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v10" />
      <path d="M4 16s2-2 4-2 4 2 6 2 4-2 4-2" />
      <path d="M12 9V5M9 5a1.5 1.5 0 1 1 3 0 1.5 1.5 0 1 1 3 0" />
    </svg>,

  Syringe: (p) =>
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" {...p}>
      <path d="m18 2 4 4" />
      <path d="m17 7 3-3" />
      <path d="M19 9 8.7 19.3a1 1 0 0 1-.6.3l-3.4.7a1 1 0 0 1-1-1l.7-3.4a1 1 0 0 1 .3-.6L15 5z" />
      <path d="m9 11 4 4" />
    </svg>,

  Print: (p) =>
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" {...p}>
      <path d="M6 9V2h12v7M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2" />
      <rect x="6" y="14" width="12" height="8" />
    </svg>,

  Download: (p) =>
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" {...p}>
      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3" />
    </svg>,

  Upload: (p) =>
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" {...p}>
      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12" />
    </svg>,

  Check: (p) =>
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" {...p}>
      <path d="M20 6 9 17l-5-5" />
    </svg>,

  Close: (p) =>
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}>
      <path d="M18 6 6 18M6 6l12 12" />
    </svg>,

  Info: (p) =>
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" {...p}>
      <circle cx="12" cy="12" r="10" />
      <path d="M12 16v-4M12 8h.01" />
    </svg>,

  Alert: (p) =>
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" {...p}>
      <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
      <path d="M12 9v4M12 17h.01" />
    </svg>,

  User: (p) =>
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" {...p}>
      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
      <circle cx="12" cy="7" r="4" />
    </svg>,

  Logout: (p) =>
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" {...p}>
      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
      <polyline points="16,17 21,12 16,7" />
      <line x1="21" y1="12" x2="9" y2="12" />
    </svg>,

  Copy: (p) =>
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" {...p}>
      <rect x="9" y="9" width="13" height="13" rx="2" />
      <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
    </svg>

};

/* ---------------- Helpers ---------------- */

function formatDate(iso, opts = {}) {
  if (!iso) return "—";
  const d = new Date(iso);
  if (isNaN(d)) return iso;
  if (opts.short) {
    return d.toLocaleDateString("de-CH", { day: "2-digit", month: "2-digit", year: "numeric" });
  }
  return d.toLocaleDateString("de-CH", { day: "2-digit", month: "short", year: "numeric" });
}

function calcAge(dob) {
  const b = new Date(dob);
  const n = new Date();
  let a = n.getFullYear() - b.getFullYear();
  const m = n.getMonth() - b.getMonth();
  if (m < 0 || m === 0 && n.getDate() < b.getDate()) a--;
  return a;
}

function initials(first, last) {
  return (first?.[0] || "") + (last?.[0] || "");
}

/* ---------------- TopBar ---------------- */

function TopBar({ crumbs = [], onCrumb }) {
  const d = window.AppData.doctor;
  const logout = () => { sessionStorage.removeItem('auth'); window.location.replace('/'); };
  return (
    <header className="topbar">
      <div className="topbar-inner">
        <div className="brand-mark">
          <div className="brand-logo" aria-hidden="true" />
          <div className="brand-text">
            <span className="name">Impfdossier <span style={{ color: "var(--swiss-red)" }}>CH</span></span>
            <span className="sub">Praxis-Cockpit</span>
          </div>
        </div>

        {/* Breadcrumb removed — page heading already conveys location */}
        <div className="topbar-spacer" />

        <div className="doc-chip" title={`GLN ${d.gln}`}>
          <div className="doc-avatar">{d.initials}</div>
          <div className="doc-info">
            <div className="doc-name">{d.name}</div>
            <div className="doc-meta">
              <span>{d.praxis}</span>
              <span className="dot">·</span>
              <span className="gln">GLN&nbsp;{d.gln}</span>
            </div>
          </div>
        </div>

        <button className="logout-btn" onClick={logout} title="Abmelden">
          <Icon.Logout /> <span>Abmelden</span>
        </button>
      </div>
    </header>);

}

/* ---------------- Patient avatar ---------------- */

function PatientAvatar({ patient, size = 32 }) {
  // Deterministic background based on lastName.
  const palettes = [
  ["#E8F0F7", "#0B4A7A"],
  ["#E5F4F1", "#0F8E7E"],
  ["#FBF2E2", "#B45309"],
  ["#FBEAE7", "#B42318"],
  ["#EEF0F4", "#3D4A5C"],
  ["#EDE7F6", "#5E35B1"]];

  const key = (patient.lastName.charCodeAt(0) + patient.firstName.charCodeAt(0)) % palettes.length;
  const [bg, fg] = palettes[key];
  return (
    <div
      style={{
        width: size, height: size, borderRadius: "50%",
        background: bg, color: fg,
        display: "grid", placeItems: "center",
        fontWeight: 600, fontSize: size * 0.36, flexShrink: 0,
        letterSpacing: "0.02em"
      }}>
      
      {initials(patient.firstName, patient.lastName)}
    </div>);

}

Object.assign(window, { Icon, formatDate, calcAge, initials, TopBar, PatientAvatar });