// Screen 1 — Patient list with search & filter

function PatientList({ onOpenPatient }) {
  const t = useT();
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
          <h1 className="page-title">{t("list.title")}</h1>
        </div>
      </div>

      {/* Search + filters bar */}
      <div className="filters-bar">
        <div className="search-wrap">
          <Icon.Search className="search-icon" />
          <input
            className="search-input"
            placeholder={t("list.searchPlaceholder")}
            value={query}
            onChange={(e) => setQuery(e.target.value)} />
          
          {query &&
          <button className="search-clear" onClick={() => setQuery("")} aria-label={t("list.clearSearch")}>
              <Icon.Close />
            </button>
          }
          <div className="kbd-hint">⌘K</div>
        </div>

        <div className="chip-group" role="tablist" aria-label={t("list.sexFilter")}>
          {[
          { v: "all", l: t("list.all") },
          { v: "F", l: t("list.female") },
          { v: "M", l: t("list.male") }].
          map((o) =>
          <button key={o.v} className={"chip" + (sex === o.v ? " is-active" : "")} onClick={() => setSex(o.v)}>{o.l}</button>
          )}
        </div>

        <div className="chip-group" role="tablist" aria-label={t("list.ageFilter")}>
          {[
          { v: "all", l: t("list.allAges") },
          { v: "child", l: "0–17" },
          { v: "adult", l: "18–64" },
          { v: "senior", l: "65+" }].
          map((o) =>
          <button key={o.v} className={"chip" + (ageBand === o.v ? " is-active" : "")} onClick={() => setAgeBand(o.v)}>{o.l}</button>
          )}
        </div>

        <div className="sort-wrap-quiet">
            <select className="sort-select-quiet" value={sortBy} onChange={(e) => setSortBy(e.target.value)} aria-label={t("list.sort")}>
              <option value="name">{t("list.sortByName")}</option>
              <option value="age">{t("list.sortByAge")}</option>
            </select>
        </div>
      </div>

      {/* Result count */}
      <div className="result-meta">
        <span>{t("list.resultCount", { count: filtered.length })}</span>
        {(query || sex !== "all" || ageBand !== "all") &&
        <button className="reset-link" onClick={() => {setQuery("");setSex("all");setAgeBand("all");}}>
            {t("list.resetFilters")}
          </button>
        }
        <div style={{ marginLeft: "auto" }}>
          <button className="btn btn-primary btn-sm" onClick={() => setShowAdd(true)}>
            <Icon.Plus /> {t("list.addPatient")}
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
              <th style={{ width: "36%" }}>{t("list.columnPatient")}</th>
              <th style={{ width: "20%" }}>{t("list.columnBirthDate")}</th>
              <th style={{ width: "40%" }}>{t("list.columnAddress")}</th>
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
                      <div className="sub-line">{t("sex." + p.sex)}</div>
                    </div>
                  </div>
                </td>
                <td>
                  <div className="dob-line tnum">{formatDate(p.dob, { short: true })}</div>
                  <div className="sub-line">{t("common.years", { count: p.age })}</div>
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
                    <div className="empty-title">{t("list.emptyTitle")}</div>
                    <div className="empty-sub">{t("list.emptyHint")}</div>
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
  const t = useT();
  const [form, setForm] = useState({ lastName: "", firstName: "", gender: "MÄNNLICH", birthDate: "", ahv: "", street: "", streetNumber: "", zipCode: "", city: "", email: "", phone: "" });
  const [touched, setTouched] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [serverError, setServerError] = useState(null);

  const set = (k, v) => setForm((prev) => ({ ...prev, [k]: v }));
  const touch = (k) => setTouched((prev) => ({ ...prev, [k]: true }));

  const todayIso = localIsoToday();
  const [birthDateIncomplete, setBirthDateIncomplete] = useState(false);
  const handleBirthDateChange = (input) => {
    setBirthDateIncomplete(input.validity.badInput);
    set("birthDate", input.value);
  };

  const formatAhvInput = (value) => {
    const digits = value.replace(/\D/g, "").slice(0, 13);
    let formatted = [digits.slice(0, 3), digits.slice(3, 7), digits.slice(7, 11), digits.slice(11)]
      .filter(Boolean)
      .join(".");
    if (/\.\s*$/.test(value) && [3, 7, 11].includes(digits.length)) formatted += ".";
    return formatted;
  };

  const handleAhvChange = (e) => {
    const input = e.target;
    const raw = input.value;
    const caret = input.selectionStart ?? raw.length;
    const formatted = formatAhvInput(raw);
    set("ahv", formatted);

    if (caret < raw.length) {
      const digitsBeforeCaret = raw.slice(0, caret).replace(/\D/g, "").length;
      let pos = 0;
      for (let seen = 0; pos < formatted.length && seen < digitsBeforeCaret; pos++) {
        if (/\d/.test(formatted[pos])) seen++;
      }
      requestAnimationFrame(() => input.setSelectionRange(pos, pos));
    }
  };

  const hasValidAhvCheckDigit = (digits) => {
    const sum = [...digits.slice(0, 12)].reduce((acc, d, i) => acc + Number(d) * (i % 2 ? 3 : 1), 0);
    return (10 - (sum % 10)) % 10 === Number(digits[12]);
  };

  const errors = {};
  if (!form.lastName.trim()) errors.lastName = t("common.required");
  if (!form.firstName.trim()) errors.firstName = t("common.required");
  if (!form.birthDate) errors.birthDate = birthDateIncomplete ? t("common.invalidDate") : t("common.required");
  else if (form.birthDate > todayIso) errors.birthDate = t("addPatient.birthDateFuture");

  const ahvDigits = normalizeAhv(form.ahv);
  let ahvErrorIsLive = false;
  if (ahvDigits) {
    if (!"756".startsWith(ahvDigits.slice(0, 3))) {
      errors.ahv = t("addPatient.ahvPrefix");
      ahvErrorIsLive = true;
    } else if (ahvDigits.length < 13) {
      errors.ahv = t("addPatient.ahvIncomplete");
    } else if (!hasValidAhvCheckDigit(ahvDigits)) {
      errors.ahv = t("addPatient.ahvCheckDigit");
      ahvErrorIsLive = true;
    }
  }
  const showAhvError = errors.ahv && (ahvErrorIsLive || touched.ahv);

  const isValid = Object.keys(errors).length === 0;

  const submit = async () => {
    setTouched({ lastName: true, firstName: true, birthDate: true, ahv: true });
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
        ahv: normalizeAhv(form.ahv),
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
      <aside className="sheet" onClick={(e) => e.stopPropagation()} role="dialog" aria-label={t("addPatient.title")}>
        <header className="sheet-head">
          <div>
            <div className="sheet-eyebrow">{t("addPatient.eyebrow")}</div>
            <h2 className="sheet-title">{t("addPatient.title")}</h2>
          </div>
          <button className="btn-icon btn-ghost" onClick={onCancel} aria-label={t("common.close")}>
            <Icon.Close />
          </button>
        </header>

        <div className="sheet-body">
          <div className="grid-2">
            <div className="field">
              <label className="field-label">{t("addPatient.lastName")}<span className="req">*</span></label>
              <input className={"input" + (touched.lastName && errors.lastName ? " is-error" : "")}
                placeholder={t("common.example", { value: "Müller" })}
                value={form.lastName}
                onChange={(e) => set("lastName", e.target.value)}
                onBlur={() => touch("lastName")} />
              {touched.lastName && errors.lastName && <div className="field-error">{errors.lastName}</div>}
            </div>

            <div className="field">
              <label className="field-label">{t("addPatient.firstName")}<span className="req">*</span></label>
              <input className={"input" + (touched.firstName && errors.firstName ? " is-error" : "")}
                placeholder={t("common.example", { value: "Anna" })}
                value={form.firstName}
                onChange={(e) => set("firstName", e.target.value)}
                onBlur={() => touch("firstName")} />
              {touched.firstName && errors.firstName && <div className="field-error">{errors.firstName}</div>}
            </div>

            <div className="field">
              <label className="field-label">{t("addPatient.gender")}<span className="req">*</span></label>
              <select className="select" value={form.gender} onChange={(e) => set("gender", e.target.value)}>
                <option value="MÄNNLICH">{t("addPatient.genderMale")}</option>
                <option value="WEIBLICH">{t("addPatient.genderFemale")}</option>
                <option value="DIVERS">{t("addPatient.genderDiverse")}</option>
              </select>
            </div>

            <div className="field">
              <label className="field-label">{t("addPatient.birthDate")}<span className="req">*</span></label>
              <input className={"input tnum" + (touched.birthDate && errors.birthDate ? " is-error" : "")}
                type="date"
                max={todayIso}
                value={form.birthDate}
                onChange={(e) => handleBirthDateChange(e.target)}
                onBlur={(e) => { setBirthDateIncomplete(e.target.validity.badInput); touch("birthDate"); }} />
              {touched.birthDate && errors.birthDate && <div className="field-error">{errors.birthDate}</div>}
            </div>

            <div className="field" style={{ gridColumn: "1 / span 1" }}>
              <label className="field-label">{t("addPatient.street")}</label>
              <input className="input" placeholder={t("common.example", { value: "Bahnhofstrasse" })}
                value={form.street}
                onChange={(e) => set("street", e.target.value)} />
            </div>

            <div className="field" style={{ gridColumn: "2 / span 1" }}>
              <label className="field-label">{t("addPatient.streetNumber")}</label>
              <input className="input" placeholder={t("common.example", { value: "12" })}
                value={form.streetNumber}
                onChange={(e) => set("streetNumber", e.target.value)} />
            </div>

            <div className="field">
              <label className="field-label">{t("addPatient.zipCode")}</label>
              <input className="input tnum" placeholder={t("common.example", { value: "8001" })}
                value={form.zipCode}
                onChange={(e) => set("zipCode", e.target.value)} />
            </div>

            <div className="field">
              <label className="field-label">{t("addPatient.city")}</label>
              <input className="input" placeholder={t("common.example", { value: "Zürich" })}
                value={form.city}
                onChange={(e) => set("city", e.target.value)} />
            </div>

            <div className="field" style={{ gridColumn: "1 / -1" }}>
              <label className="field-label">{t("addPatient.ahv")}</label>
              <input className={"input tnum" + (showAhvError ? " is-error" : "")}
                type="text"
                inputMode="numeric"
                autoComplete="off"
                placeholder="756.1234.5678.97"
                maxLength={16}
                value={form.ahv}
                onChange={handleAhvChange}
                onBlur={() => touch("ahv")} />
              {showAhvError && <div className="field-error">{errors.ahv}</div>}
            </div>

            <div className="field">
              <label className="field-label">{t("addPatient.email")}</label>
              <input className="input" type="email" placeholder={t("common.example", { value: "anna.mueller@example.ch" })}
                value={form.email}
                onChange={(e) => set("email", e.target.value)} />
            </div>

            <div className="field">
              <label className="field-label">{t("addPatient.phone")}</label>
              <input className="input tnum" type="tel" placeholder={t("common.example", { value: "+41 79 123 45 67" })}
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
            <button className="btn" onClick={onCancel} disabled={submitting}>{t("common.cancel")}</button>
            <button className="btn btn-primary" onClick={submit} disabled={submitting}>
              <Icon.Plus /> {submitting ? t("common.saving") : t("addPatient.save")}
            </button>
          </div>
        </footer>
      </aside>
    </div>
  );
}

window.PatientList = PatientList;