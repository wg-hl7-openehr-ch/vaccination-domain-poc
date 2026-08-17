// Screen 3 — New vaccination form (modal-style sheet)

function VaccinationForm({ patient, onCancel, onVaccinationCreated }) {
  const { vaccineCatalog, manufacturers, routes, sites, reasons } = window.AppData;
  const today = new Date().toISOString().slice(0, 10);

  const [form, setForm] = useState({
    vaccine: "",
    vaccineCode: "",
    date: today,
    manufacturer: "",
    batch: "",
    organization: "Hausarztpraxis Bahnhofstrasse, Zürich",
    expiry: "",
    route: "i.m.",
    amount: "0.5",
    unit: "ml",
    site: "Oberarm links (M. deltoideus)",
    reason: "373068000",
    doseNumber: "",
    doseTotal: "",
    hasAdverse: false,
    adverseText: ""
  });

  const [touched, setTouched] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const SUPPRESS_ERROR_ON_SUBMIT = true;
  const set = (k, v) => setForm((prev) => ({ ...prev, [k]: v }));
  const touch = (k) => setTouched((prev) => ({ ...prev, [k]: true }));

  const errors = {};
  if (!form.vaccine) errors.vaccine = "Pflichtfeld";
  if (!form.date) errors.date = "Pflichtfeld";
  if (!form.manufacturer) errors.manufacturer = "Pflichtfeld";
  if (!form.batch) errors.batch = "Pflichtfeld";
  if (!form.amount) errors.amount = "Pflichtfeld";

  const isValid = Object.keys(errors).length === 0;

  const submit = async () => {
    if (!isValid) {
      setTouched({ vaccine: 1, date: 1, manufacturer: 1, batch: 1, amount: 1 });
      return;
    }

    setSubmitting(true);
    try {
      const doseNum = parseInt(form.doseNumber, 10) || 1;
      const seriesDoses = form.doseTotal ? parseInt(form.doseTotal, 10) : null;
      const amountVal = parseFloat(form.amount) || 0;

      const selectedReason = reasons.find(r => r.code === form.reason) || reasons[0];
      const payload = {
        vaccineName: form.vaccine,
        vaccineCode: form.vaccineCode,
        marketingAuthorizationHolder: form.manufacturer,
        lotNumber: form.batch,
        expiryDate: form.expiry || null,
        vaccinationDate: form.date,
        routeOfAdministration: DataService.routeToApi(form.route),
        administeredDose: { value: amountVal, unit: form.unit },
        siteOfAdministration: form.site,
        vaccinationReason: {
          code: selectedReason.code,
          display: selectedReason.display,
          swissLabel: selectedReason.swissLabel
        },
        doseNumber: doseNum,
        seriesDoses: seriesDoses,
        adverseReactionObserved: form.hasAdverse,
      };

      const created = await DataService.createImmunization(patient.id, payload);
      const record = DataService.transformVaccination(created);
      record.note = form.hasAdverse ? 'UAW: ' + form.adverseText : null;
      onVaccinationCreated(record);
    } catch (err) {
      console.error('Fehler beim Speichern der Impfung:', err);
      if (SUPPRESS_ERROR_ON_SUBMIT) {
        onCancel();
      } else {
        alert('Fehler beim Speichern der Impfung: ' + err.message);
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="sheet-backdrop" onClick={onCancel}>
      <aside className="sheet" onClick={(e) => e.stopPropagation()} role="dialog" aria-label="Neue Impfung erfassen">
        <header className="sheet-head">
          <div>
            <div className="sheet-eyebrow">Neue Impfung</div>
            <h2 className="sheet-title">Impfeintrag erfassen</h2>
            <div className="sheet-sub">
              für <b>{patient.lastName}, {patient.firstName}</b>
              <span className="muted"> · {formatDate(patient.dob, { short: true })}</span>
            </div>
          </div>
          <button className="btn-icon btn-ghost" onClick={onCancel} aria-label="Schliessen">
            <Icon.Close />
          </button>
        </header>

        <div className="sheet-body">
          <FormGroup title="Impfstoff" eyebrow="1">
            <div className="grid-2">
              <Field label="Impfstoff" required>
                <select className="select" value={form.vaccine} onChange={(e) => set("vaccine", e.target.value)}>
                  {vaccineCatalog.map((r) => <option key={r.combined} value={r.combined}>{r.display}</option>)}
                </select>
              </Field>
              <Field label="Impfstoffcode" hint="z. B. ATC- oder GTIN-Code" required>
                <input className="input mono" placeholder="J07CA02" value={form.vaccineCode}
                onChange={(e) => set("vaccineCode", e.target.value)} />
              </Field>
              <Autocomplete
                label="Zulassungsinhaberin / Hersteller"
                required
                placeholder="z. B. GlaxoSmithKline"
                value={form.manufacturer}
                onChange={(v) => set("manufacturer", v)}
                onBlur={() => touch("manufacturer")}
                options={manufacturers}
                error={touched.manufacturer && errors.manufacturer}
              />
              <Field label="Chargennummer" required hint="Auf der Impfstoffpackung — z. B. EW0150" error={touched.batch && errors.batch}>
                <input className="input mono" placeholder="A1B2C3" value={form.batch}
                onChange={(e) => set("batch", e.target.value.toUpperCase())} onBlur={() => touch("batch")} />
              </Field>
              <Field label="Verfallsdatum" hint="laut Packung">
                <input className="input tnum" type="date" value={form.expiry} onChange={(e) => set("expiry", e.target.value)} />
              </Field>
            </div>
          </FormGroup>

          <FormGroup title="Verabreichung" eyebrow="2">
            <div className="grid-2">
              <Field label="Impfdatum" required error={touched.date && errors.date}>
                <input className="input tnum" type="date" value={form.date}
                onChange={(e) => set("date", e.target.value)} onBlur={() => touch("date")} />
              </Field>

              <Field label="Applikationsweg" required>
                <div className="seg" role="radiogroup">
                  {routes.map((r) =>
                  <button key={r.value}
                  role="radio"
                  aria-checked={form.route === r.value}
                  className={"seg-btn" + (form.route === r.value ? " is-active" : "")}
                  onClick={() => set("route", r.value)}>
                      {r.value}
                    </button>
                  )}
                </div>
                <div className="field-hint">{routes.find((r) => r.value === form.route)?.label}</div>
              </Field>

              <Field label="Verabreichte Menge" required error={touched.amount && errors.amount}>
                <div className="input-affix">
                  <input className="input tnum" inputMode="decimal" placeholder="0.5" value={form.amount}
                  onChange={(e) => set("amount", e.target.value)} onBlur={() => touch("amount")} />
                  <select className="select affix-select" value={form.unit} onChange={(e) => set("unit", e.target.value)}>
                    <option>ml</option>
                    <option>Dosis</option>
                    <option>Tropfen</option>
                  </select>
                </div>
              </Field>

              <Field label="Applikationsort" required style={{ gridColumn: "1 / -1" }}>
                <select className="select" value={form.site} onChange={(e) => set("site", e.target.value)}>
                  {sites.map((s) => <option key={s}>{s}</option>)}
                </select>
              </Field>
            </div>
          </FormGroup>

          <FormGroup title="Klinischer Kontext" eyebrow="3">
            <div className="grid-2">
              <Field label="Impfgrund" required>
                <select className="select" value={form.reason} onChange={(e) => set("reason", e.target.value)}>
                  {reasons.map((r) => <option key={r.code} value={r.code}>{r.swissLabel}</option>)}
                </select>
              </Field>

              <Field label="Dosisnummer in Impfserie">
                <div className="dose-row">
                  <input className="input tnum" placeholder="2" value={form.doseNumber}
                  onChange={(e) => set("doseNumber", e.target.value.replace(/[^\d]/g, ""))} />
                  <span className="dose-sep">von</span>
                  <input className="input tnum" placeholder="3" value={form.doseTotal}
                  onChange={(e) => set("doseTotal", e.target.value.replace(/[^\d]/g, ""))} />
                  <span className="field-hint" style={{ marginLeft: 8 }}>
                    {form.doseNumber && form.doseTotal ? "Dosis " + form.doseNumber + " von " + form.doseTotal : "Booster? Leer lassen."}
                  </span>
                </div>
              </Field>
            </div>

            <Field label="Unerwünschte Reaktion nach Impfung (UAW)" hint="Nur ausfüllen, falls eine Reaktion beobachtet wurde — meldepflichtig an Swissmedic.">
              <label className="toggle-row">
                <input type="checkbox" checked={form.hasAdverse} onChange={(e) => set("hasAdverse", e.target.checked)} />
                <span>Reaktion beobachtet</span>
              </label>
              {form.hasAdverse &&
              <>
                  <textarea
                  className="textarea"
                  placeholder="z. B. Lokalreaktion (Rötung, Schwellung), Fieber, anaphylaktische Reaktion …"
                  value={form.adverseText}
                  onChange={(e) => set("adverseText", e.target.value)} />

                  <div className="adverse-warn">
                    <Icon.Alert />
                    <span>Schwerwiegende UAW sind via <b>ElViS</b> an Swissmedic zu melden.</span>
                  </div>
                </>
              }
            </Field>
          </FormGroup>

          <div className="audit-trail">
            <div className="audit-icon"><Icon.Info /></div>
            <div className="audit-text">
              Dieser Eintrag wird signiert mit <b>{window.AppData.doctor?.name || 'Dr. med. Sarah Müller'}</b> · GLN <span className="mono">{window.AppData.doctor?.gln || '7601000123456'}</span>
              <br />Zeitstempel: <span className="tnum">{new Date().toLocaleString("de-CH")}</span>
            </div>
          </div>
        </div>

        <footer className="sheet-foot">
          <div className="foot-left">
            {!isValid && Object.keys(touched).length > 0 &&
            <span className="foot-error"><Icon.Alert /> Bitte alle Pflichtfelder ausfüllen.</span>
            }
          </div>
          <div className="foot-right">
            <button className="btn" onClick={onCancel} disabled={submitting}>Abbrechen</button>
            <button className="btn" onClick={submit} disabled={!isValid || submitting} style={(!isValid || submitting) ? { opacity: 0.5 } : null}>
              {submitting ? 'Speichert …' : 'Speichern & weiter erfassen'}
            </button>
            <button className="btn btn-primary" onClick={submit} disabled={!isValid || submitting} style={(!isValid || submitting) ? { opacity: 0.5 } : null}>
              <Icon.Check /> {submitting ? 'Speichert …' : 'Impfung speichern'}
            </button>
          </div>
        </footer>
      </aside>
    </div>);

}

/* ---------- Form helpers ---------- */

function FormGroup({ title, eyebrow, children }) {
  return (
    <section className="form-group">
      <header className="form-group-head">
        <span className="form-group-eyebrow">{eyebrow}</span>
        <h3 className="form-group-title">{title}</h3>
      </header>
      <div className="form-group-body">{children}</div>
    </section>);

}

function Field({ label, required, hint, error, children, style }) {
  return (
    <div className="field" style={style}>
      <label className="field-label">
        {label}{required && <span className="req">*</span>}
      </label>
      {children}
      {error ?
      <div className="field-error"><Icon.Alert /> {error}</div> :
      hint && <div className="field-hint">{hint}</div>}
    </div>);

}

function inferDiseaseFromVaccine(vaccine) {
  return DataService.inferDisease(vaccine);
}

/* ---------- Custom liquidglas Autocomplete component ---------- */

function Autocomplete({ label, value, onChange, onBlur, options, placeholder, required, error, hint }) {
  const [isOpen, setIsOpen] = useState(false);
  const [search, setSearch] = useState(value || "");
  const containerRef = useRef(null);

  useEffect(() => {
    setSearch(value || "");
  }, [value]);

  const filteredOptions = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return options;
    return options.filter(opt => opt.toLowerCase().includes(q));
  }, [options, search]);

  useEffect(() => {
    function handleClickOutside(event) {
      if (containerRef.current && !containerRef.current.contains(event.target)) {
        setIsOpen(false);
        if (onBlur) onBlur();
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [search, value, onBlur]);

  const selectOption = (opt) => {
    onChange(opt);
    setSearch(opt);
    setIsOpen(false);
  };

  return (
    <div className="autocomplete-container" ref={containerRef} style={{ position: "relative" }}>
      <Field label={label} required={required} error={error} hint={hint}>
        <div className="input-wrap" style={{ position: "relative" }}>
          <input
            className="input"
            type="text"
            placeholder={placeholder}
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              onChange(e.target.value);
              setIsOpen(true);
            }}
            onFocus={() => setIsOpen(true)}
          />
          <button
            type="button"
            className="btn-icon btn-ghost autocomplete-toggle"
            onClick={() => setIsOpen(!isOpen)}
            style={{
              position: "absolute",
              right: 4,
              top: "50%",
              transform: "translateY(-50%)",
              border: 0,
              cursor: "pointer",
              padding: 6,
              color: "var(--text-3)",
              display: "flex",
              alignItems: "center",
              background: "transparent"
            }}
            aria-label="Liste öffnen"
          >
            <Icon.ChevronDown />
          </button>
        </div>
      </Field>
      {isOpen && filteredOptions.length > 0 && (
        <ul
          className="autocomplete-dropdown"
          role="listbox"
          style={{
            position: "absolute",
            top: "calc(100% + 4px)",
            left: 0,
            width: "100%",
            maxHeight: "180px",
            overflowY: "auto",
            border: "1px solid var(--border)",
            borderRadius: "var(--r-md)",
            boxShadow: "var(--shadow-lg)",
            zIndex: 1000,
            listStyle: "none",
            margin: 0,
            padding: "4px 0",
            backdropFilter: "blur(20px)",
            backgroundColor: "rgba(255, 255, 255, 0.92)",
            border: "1px solid rgba(255, 255, 255, 0.6)"
          }}
        >
          {filteredOptions.map((opt) => (
            <li
              key={opt}
              role="option"
              aria-selected={value === opt}
              onClick={() => selectOption(opt)}
              style={{
                padding: "8px 12px",
                cursor: "pointer",
                fontSize: "13px",
                color: value === opt ? "var(--brand)" : "var(--text)",
                backgroundColor: value === opt ? "var(--brand-50)" : "transparent",
                fontWeight: value === opt ? "600" : "400"
              }}
              onMouseEnter={(e) => {
                if (value !== opt) e.target.style.backgroundColor = "var(--surface-hover)";
              }}
              onMouseLeave={(e) => {
                if (value !== opt) e.target.style.backgroundColor = "transparent";
              }}
            >
              {opt}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

window.VaccinationForm = VaccinationForm;
