// Screen 1 — Patient list with search & filter

function PatientList({ onOpenPatient }) {
  const [patients, setPatients] = useState(window.AppData.patients || []);
  const [showAdd, setShowAdd] = useState(false);
  const [query, setQuery] = useState("");
  const [sex, setSex] = useState("all"); // all | M | F
  const [ageBand, setAgeBand] = useState("all"); // all | child | adult | senior
  const [sortBy, setSortBy] = useState("name"); // name | age

  const enriched = useMemo(() => patients.map((p) => {
    return { ...p, age: calcAge(p.dob) };
  }), [patients]);

  const filtered = useMemo(() => {
    let list = enriched;
    if (sex !== "all") list = list.filter((p) => p.sex === sex);
    if (ageBand !== "all") {
      list = list.filter((p) => {
        if (ageBand === "child") return p.age < 18;
        if (ageBand === "adult") return p.age >= 18 && p.age < 65;
        if (ageBand === "senior") return p.age >= 65;
        return true;
      });
    }
    if (query.trim()) {
      const q = query.trim().toLowerCase();
      list = list.filter((p) =>
      p.firstName.toLowerCase().includes(q) ||
      p.lastName.toLowerCase().includes(q) ||
      p.email.toLowerCase().includes(q) ||
      p.address.toLowerCase().includes(q) ||
      p.phone.replace(/\s+/g, "").includes(q.replace(/\s+/g, "")) ||
      p.dob.includes(q) ||
      p.id.toLowerCase().includes(q)
      );
    }
    if (sortBy === "name") list = [...list].sort((a, b) => a.lastName.localeCompare(b.lastName));
    if (sortBy === "age") list = [...list].sort((a, b) => b.age - a.age);
    return list;
  }, [enriched, query, sex, ageBand, sortBy]);

  return (
    <main className="page">
      <div className="page-head">
        <div>
          <h1 className="page-title">Patient:innen</h1>
        </div>
      </div>

      {/* Search + filters bar */}
      <div className="filters-bar">
        <div className="search-wrap">
          <Icon.Search className="search-icon" />
          <input
            className="search-input"
            placeholder="Suche nach..."
            value={query}
            onChange={(e) => setQuery(e.target.value)} />
          
          {query &&
          <button className="search-clear" onClick={() => setQuery("")} aria-label="Suche leeren">
              <Icon.Close />
            </button>
          }
          <div className="kbd-hint">⌘K</div>
        </div>

        <div className="chip-group" role="tablist" aria-label="Geschlecht">
          {[
          { v: "all", l: "Alle" },
          { v: "F", l: "Weiblich" },
          { v: "M", l: "Männlich" }].
          map((o) =>
          <button key={o.v} className={"chip" + (sex === o.v ? " is-active" : "")} onClick={() => setSex(o.v)}>{o.l}</button>
          )}
        </div>

        <div className="chip-group" role="tablist" aria-label="Altersgruppe">
          {[
          { v: "all", l: "Alle Alter" },
          { v: "child", l: "0–17" },
          { v: "adult", l: "18–64" },
          { v: "senior", l: "65+" }].
          map((o) =>
          <button key={o.v} className={"chip" + (ageBand === o.v ? " is-active" : "")} onClick={() => setAgeBand(o.v)}>{o.l}</button>
          )}
        </div>

        <div className="sort-wrap-quiet">
            <select className="sort-select-quiet" value={sortBy} onChange={(e) => setSortBy(e.target.value)} aria-label="Sortieren">
              <option value="name">Sortiert: Nachname A–Z</option>
              <option value="age">Sortiert: Alter</option>
            </select>
        </div>
      </div>

      {/* Result count */}
      <div className="result-meta">
        <span>{filtered.length} Treffer</span>
        {(query || sex !== "all" || ageBand !== "all") &&
        <button className="reset-link" onClick={() => {setQuery("");setSex("all");setAgeBand("all");}}>
            Filter zurücksetzen
          </button>
        }
        <div style={{ marginLeft: "auto" }}>
          <button className="btn btn-primary btn-sm" onClick={() => setShowAdd(true)}>
            <Icon.Plus /> Patient erfassen
          </button>
        </div>
      </div>

      {showAdd &&
      <AddPatientForm
        onCancel={() => setShowAdd(false)}
        onPatientAdded={(p) => {
          const updated = [...patients, p];
          window.AppData.patients = updated;
          setPatients(updated);
          setShowAdd(false);
        }} />
      }

      {/* Patient table */}
      <div className="card patient-table-card">
        <table className="patient-table">
          <thead>
            <tr>
              <th style={{ width: "36%" }}>Patient:in</th>
              <th style={{ width: "20%" }}>Geburtsdatum</th>
              <th style={{ width: "40%" }}>Adresse</th>
              <th style={{ width: "4%" }}></th>
            </tr>
          </thead>
          <tbody>
            {filtered.map((p) =>
            <tr key={p.id} className="patient-row" onClick={() => onOpenPatient(p.id)} tabIndex={0}
            onKeyDown={(e) => {if (e.key === "Enter") onOpenPatient(p.id);}}>
                <td>
                  <div className="cell-patient">
                    <PatientAvatar patient={p} />
                    <div className="cell-patient-text">
                      <div className="name-line">
                        <span className="last">{p.lastName}</span>, <span className="first">{p.firstName}</span>
                      </div>
                      <div className="sub-line">{p.sex === "F" ? "weiblich" : "männlich"}</div>
                    </div>
                  </div>
                </td>
                <td>
                  <div className="dob-line tnum">{formatDate(p.dob, { short: true })}</div>
                  <div className="sub-line">{p.age} Jahre</div>
                </td>
                <td>
                  <div className="addr-line">{p.address}</div>
                </td>
                <td>
                  <div className="row-cta"><Icon.Chevron /></div>
                </td>
              </tr>
            )}
            {filtered.length === 0 &&
            <tr>
                <td colSpan={4}>
                  <div className="empty-state">
                    <div className="empty-icon"><Icon.Search /></div>
                    <div className="empty-title">Keine Patient:innen gefunden</div>
                    <div className="empty-sub">Passe deine Suche oder die Filter an.</div>
                  </div>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </main>);

}

// ---- Add Patient sheet ----

function AddPatientForm({ onCancel, onPatientAdded }) {
  const [form, setForm] = useState({ lastName: "", firstName: "", gender: "MÄNNLICH", birthDate: "", ahv: "", street: "", streetNumber: "", zipCode: "", city: "", email: "", phone: "" });
  const [touched, setTouched] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [serverError, setServerError] = useState(null);

  const set = (k, v) => setForm((prev) => ({ ...prev, [k]: v }));
  const touch = (k) => setTouched((prev) => ({ ...prev, [k]: true }));

  const errors = {};
  if (!form.lastName.trim()) errors.lastName = "Pflichtfeld";
  if (!form.firstName.trim()) errors.firstName = "Pflichtfeld";
  if (!form.birthDate) errors.birthDate = "Pflichtfeld";
  const isValid = Object.keys(errors).length === 0;

  const submit = async () => {
    setTouched({ lastName: true, firstName: true, birthDate: true });
    if (!isValid) return;
    setSubmitting(true);
    setServerError(null);
    try {
      const payload = {
        lastName: form.lastName.trim(),
        firstName: form.firstName.trim(),
        birthDate: form.birthDate,
        gender: form.gender,
        address: {
          street: (form.street.trim() + (form.streetNumber.trim() ? " " + form.streetNumber.trim() : "")).trim(),
          zipCode: form.zipCode.trim(),
          city: form.city.trim(),
        },
        email: form.email.trim(),
        phoneNumber: form.phone.trim(),
        ahv: form.ahv.trim(),
      };
      let created;
      try {
        const raw = await DataService.createPatient(payload);
        created = DataService.transformPatient(raw, 0);
      } catch {
        // backend not yet wired — add locally with a temporary id
        const tempId = "TMP-" + Date.now();
        const genderMap = { "MÄNNLICH": "M", "WEIBLICH": "F", "DIVERS": "D" };
        const addrStr = [payload.address.street, payload.address.zipCode, payload.address.city].filter(Boolean).join(", ");
        created = {
          id: tempId,
          firstName: payload.firstName,
          lastName: payload.lastName,
          dob: payload.birthDate,
          sex: genderMap[payload.gender] || "M",
          address: addrStr,
          email: payload.email,
          phone: payload.phoneNumber,
          ahv: payload.ahv,
        };
      }
      onPatientAdded(created);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="sheet-backdrop" onClick={onCancel}>
      <aside className="sheet" onClick={(e) => e.stopPropagation()} role="dialog" aria-label="Patient erfassen">
        <header className="sheet-head">
          <div>
            <div className="sheet-eyebrow">Neuer Patient</div>
            <h2 className="sheet-title">Patient erfassen</h2>
          </div>
          <button className="btn-icon btn-ghost" onClick={onCancel} aria-label="Schliessen">
            <Icon.Close />
          </button>
        </header>

        <div className="sheet-body">
          <div className="grid-2">
            <div className="field">
              <label className="field-label">Nachname<span className="req">*</span></label>
              <input className={"input" + (touched.lastName && errors.lastName ? " is-error" : "")}
                placeholder="z. B. Müller"
                value={form.lastName}
                onChange={(e) => set("lastName", e.target.value)}
                onBlur={() => touch("lastName")} />
              {touched.lastName && errors.lastName && <div className="field-error">{errors.lastName}</div>}
            </div>

            <div className="field">
              <label className="field-label">Vorname<span className="req">*</span></label>
              <input className={"input" + (touched.firstName && errors.firstName ? " is-error" : "")}
                placeholder="z. B. Anna"
                value={form.firstName}
                onChange={(e) => set("firstName", e.target.value)}
                onBlur={() => touch("firstName")} />
              {touched.firstName && errors.firstName && <div className="field-error">{errors.firstName}</div>}
            </div>

            <div className="field">
              <label className="field-label">Geschlecht<span className="req">*</span></label>
              <select className="select" value={form.gender} onChange={(e) => set("gender", e.target.value)}>
                <option value="MÄNNLICH">Männlich</option>
                <option value="WEIBLICH">Weiblich</option>
                <option value="DIVERS">Divers</option>
              </select>
            </div>

            <div className="field">
              <label className="field-label">Geburtsdatum<span className="req">*</span></label>
              <input className={"input tnum" + (touched.birthDate && errors.birthDate ? " is-error" : "")}
                type="date"
                value={form.birthDate}
                onChange={(e) => set("birthDate", e.target.value)}
                onBlur={() => touch("birthDate")} />
              {touched.birthDate && errors.birthDate && <div className="field-error">{errors.birthDate}</div>}
            </div>

            <div className="field" style={{ gridColumn: "1 / span 1" }}>
              <label className="field-label">Strasse</label>
              <input className="input" placeholder="z. B. Bahnhofstrasse"
                value={form.street}
                onChange={(e) => set("street", e.target.value)} />
            </div>

            <div className="field" style={{ gridColumn: "2 / span 1" }}>
              <label className="field-label">Nummer</label>
              <input className="input" placeholder="z. B. 12"
                value={form.streetNumber}
                onChange={(e) => set("streetNumber", e.target.value)} />
            </div>

            <div className="field">
              <label className="field-label">PLZ</label>
              <input className="input tnum" placeholder="z. B. 8001"
                value={form.zipCode}
                onChange={(e) => set("zipCode", e.target.value)} />
            </div>

            <div className="field">
              <label className="field-label">Stadt</label>
              <input className="input" placeholder="z. B. Zürich"
                value={form.city}
                onChange={(e) => set("city", e.target.value)} />
            </div>

            <div className="field" style={{ gridColumn: "1 / -1" }}>
              <label className="field-label">AHV-Nummer</label>
              <input className="input tnum" placeholder="z. B. 756.1234.5678.97"
                value={form.ahv}
                onChange={(e) => set("ahv", e.target.value)} />
            </div>

            <div className="field">
              <label className="field-label">E-Mail</label>
              <input className="input" type="email" placeholder="z. B. anna.mueller@example.ch"
                value={form.email}
                onChange={(e) => set("email", e.target.value)} />
            </div>

            <div className="field">
              <label className="field-label">Telefon</label>
              <input className="input tnum" type="tel" placeholder="z. B. +41 79 123 45 67"
                value={form.phone}
                onChange={(e) => set("phone", e.target.value)} />
            </div>
          </div>
        </div>

        <footer className="sheet-foot">
          <div className="foot-left">
            {serverError && <span className="foot-error"><Icon.Alert /> {serverError}</span>}
          </div>
          <div className="foot-right">
            <button className="btn" onClick={onCancel} disabled={submitting}>Abbrechen</button>
            <button className="btn btn-primary" onClick={submit} disabled={submitting}>
              <Icon.Plus /> {submitting ? "Speichert …" : "Patient speichern"}
            </button>
          </div>
        </footer>
      </aside>
    </div>
  );
}

window.PatientList = PatientList;