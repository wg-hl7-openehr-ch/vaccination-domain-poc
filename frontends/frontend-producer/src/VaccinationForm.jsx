// Screen 3 — New vaccination form (modal-style sheet)

function VaccinationForm({ patient, onCancel, onVaccinationCreated }) {
  const t = useT();
  const { manufacturers, routes, sites, units, reasons } = window.AppData;

  const todayIso = localIsoToday();

  const [vaccineCatalog, setVaccineCatalog] = useState(window.AppData.vaccineCatalog);

  useEffect(() => {
    DataService.fetchVaccineCodes()
      .then(setVaccineCatalog)
      .catch(() => {/* keep fallback */});
  }, []);

  const [form, setForm] = useState({
    vaccine: "",
    vaccineCode: "",
    date: todayIso,
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
  const [serverError, setServerError] = useState(null);
  const SUPPRESS_ERROR_ON_SUBMIT = true;
  const set = (k, v) => {
    setForm((prev) => ({ ...prev, [k]: v }));
    setServerError(null);
  };
  const touch = (k) => setTouched((prev) => ({ ...prev, [k]: true }));
  const [incompleteDates, setIncompleteDates] = useState({});
  const markIncomplete = (k, input) => setIncompleteDates((prev) => ({ ...prev, [k]: input.validity.badInput }));
  const setDateField = (k, input) => {
    markIncomplete(k, input);
    set(k, input.value);
  };
  const blurDateField = (k, input) => {
    markIncomplete(k, input);
    touch(k);
  };

  const requiredText = t("common.required");
  const errors = {};
  if (!form.vaccine) errors.vaccine = requiredText;
  let dateErrorIsLive = false;
  const dobIso = (patient?.dob || "").slice(0, 10);
  if (!form.date) errors.date = incompleteDates.date ? t("common.invalidDate") : requiredText;
  else if (dobIso && form.date < dobIso) {
    errors.date = t("form.dateBeforeBirth", { birthDate: formatDate(dobIso, { short: true }) });
    dateErrorIsLive = true;
  } else if (form.date > todayIso) {
    errors.date = t("form.dateFuture");
    dateErrorIsLive = true;
  }
  if (incompleteDates.expiry) errors.expiry = t("common.invalidDate");
  if (!form.batch) errors.batch = requiredText;
  if (!form.amount) errors.amount = requiredText;

  const isValid = Object.keys(errors).length === 0;

  const submit = async () => {
    if (!isValid) {
      setTouched({ vaccine: 1, date: 1, expiry: 1, batch: 1, amount: 1 });
      return;
    }

    setSubmitting(true);
    setServerError(null);
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
          display: selectedReason.display
        },
        doseNumber: doseNum,
        seriesDoses: seriesDoses,
        adverseReactionObserved: form.hasAdverse,
      };

      const created = await DataService.createImmunization(patient.id, payload);
      const record = DataService.transformVaccination(created);
      record.note = form.hasAdverse ? t("form.adverseNote", { text: form.adverseText }) : null;
      onVaccinationCreated(record);
    } catch (err) {
      console.error('Fehler beim Speichern der Impfung:', err);
      if (err.status === 400) {
        setServerError(err);
      } else if (SUPPRESS_ERROR_ON_SUBMIT) {
        onCancel();
      } else {
        alert(t("form.saveFailed", { message: err.serverMessage || t("detail.tryAgainLater") }));
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="sheet-backdrop" onClick={onCancel}>
      <aside className="sheet" onClick={(e) => e.stopPropagation()} role="dialog" aria-label={t("form.dialogLabel")}>
        <header className="sheet-head">
          <div>
            <div className="sheet-eyebrow">{t("form.eyebrow")}</div>
            <h2 className="sheet-title">{t("form.title")}</h2>
            <div className="sheet-sub">
              {withElements(t("form.forPatient"), { patient: <b>{patient.lastName}, {patient.firstName}</b> })}
              <span className="muted"> · {formatDate(patient.dob, { short: true })}</span>
            </div>
          </div>
          <button className="btn-icon btn-ghost" onClick={onCancel} aria-label={t("common.close")}>
            <Icon.Close />
          </button>
        </header>

        <div className="sheet-body">
          <FormGroup title={t("form.sectionVaccine")} eyebrow="1">
            <div className="grid-2">
              <Field label={t("form.vaccine")} required>
                <select className="select" value={form.vaccine} onChange={(e) => set("vaccine", e.target.value)}>
                  {vaccineCatalog.map((r) => <option key={r.combined} value={r.combined}>{r.display}</option>)}
                </select>
              </Field>
              <Field label={t("form.batch")} required hint={t("form.batchHint")} error={touched.batch && errors.batch}>
                <input className="input mono" placeholder="A1B2C3" value={form.batch}
                onChange={(e) => set("batch", e.target.value.toUpperCase())} onBlur={() => touch("batch")} />
              </Field>
              <Field label={t("form.productNumber")} hint={t("form.productNumberHint")}>
                <input className="input mono" placeholder="7680006810037" value={form.vaccineCode}
                onChange={(e) => set("vaccineCode", e.target.value)} />
              </Field>
              <Autocomplete
                label={t("form.manufacturer")}
                placeholder={t("common.example", { value: "GlaxoSmithKline" })}
                value={form.manufacturer}
                onChange={(v) => set("manufacturer", v)}
                onBlur={() => touch("manufacturer")}
                options={manufacturers}
                error={touched.manufacturer && errors.manufacturer}
              />
             
              <Field label={t("form.expiry")} hint={t("form.expiryHint")} error={touched.expiry && errors.expiry}>
                <input className="input tnum" type="date" max="9999-12-31"
                value={form.expiry} onChange={(e) => setDateField("expiry", e.target)} onBlur={(e) => blurDateField("expiry", e.target)} />
              </Field>
            </div>
          </FormGroup>

          <FormGroup title={t("form.sectionAdministration")} eyebrow="2">
            <div className="grid-2">
              <Field label={t("form.date")} required error={(dateErrorIsLive || touched.date) && errors.date}>
                <input className="input tnum" type="date" min={dobIso || undefined} max={todayIso}
                value={form.date} onChange={(e) => setDateField("date", e.target)} onBlur={(e) => blurDateField("date", e.target)} />
              </Field>

              <Field label={t("form.route")} required>
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
                <div className="field-hint">{t(routes.find((r) => r.value === form.route)?.labelKey)}</div>
              </Field>

              <Field label={t("form.amount")} required error={touched.amount && errors.amount}>
                <div className="input-affix">
                  <input className="input tnum" inputMode="decimal" placeholder="0.5" value={form.amount}
                  onChange={(e) => set("amount", e.target.value)} onBlur={() => touch("amount")} />
                  <select className="select affix-select" value={form.unit} onChange={(e) => set("unit", e.target.value)}>
                    {units.map((u) => <option key={u.value} value={u.value}>{t(u.labelKey)}</option>)}
                  </select>
                </div>
              </Field>

              <Field label={t("form.site")} required style={{ gridColumn: "1 / -1" }}>
                <select className="select" value={form.site} onChange={(e) => set("site", e.target.value)}>
                  {sites.map((s) => <option key={s.value} value={s.value}>{t(s.labelKey)}</option>)}
                </select>
              </Field>
            </div>
          </FormGroup>

          <FormGroup title={t("form.sectionClinical")} eyebrow="3">
            <div className="grid-2">
              <Field label={t("form.reason")} required>
                <select className="select" value={form.reason} onChange={(e) => set("reason", e.target.value)}>
                  {reasons.map((r) => <option key={r.code} value={r.code}>{reasonLabel(t, r)}</option>)}
                </select>
              </Field>

              <Field label={t("form.doseNumber")}>
                <div className="dose-row">
                  <input className="input tnum" placeholder="2" value={form.doseNumber}
                  onChange={(e) => set("doseNumber", e.target.value.replace(/[^\d]/g, ""))} />
                  <span className="dose-sep">{t("form.doseOf")}</span>
                  <input className="input tnum" placeholder="3" value={form.doseTotal}
                  onChange={(e) => set("doseTotal", e.target.value.replace(/[^\d]/g, ""))} />
                  <span className="field-hint" style={{ marginLeft: 8 }}>
                    {form.doseNumber && form.doseTotal ? t("form.doseHint", { number: form.doseNumber, total: form.doseTotal }) : t("form.boosterHint")}
                  </span>
                </div>
              </Field>
            </div>

            <Field label={t("form.adverse")} hint={t("form.adverseHint")}>
              <label className="toggle-row">
                <input type="checkbox" checked={form.hasAdverse} onChange={(e) => set("hasAdverse", e.target.checked)} />
                <span>{t("form.adverseObserved")}</span>
              </label>
              {form.hasAdverse &&
              <>
                  <textarea
                  className="textarea"
                  placeholder={t("form.adversePlaceholder")}
                  value={form.adverseText}
                  onChange={(e) => set("adverseText", e.target.value)} />

                  <div className="adverse-warn">
                    <Icon.Alert />
                    <span>{withElements(t("form.adverseReport"), { system: <b>ElViS</b> })}</span>
                  </div>
                </>
              }
            </Field>
          </FormGroup>

          <div className="audit-trail">
            <div className="audit-icon"><Icon.Info /></div>
            <div className="audit-text">
              {withElements(t("form.signedBy"), { doctor: <b>{window.AppData.doctor?.name || 'Dr. med. Sarah Müller'}</b> })} · GLN <span className="mono">{window.AppData.doctor?.gln || '7601000123456'}</span>
              <br />{withElements(t("form.timestamp"), { timestamp: <span className="tnum">{formatDateTime(new Date())}</span> })}
            </div>
          </div>
        </div>

        <footer className="sheet-foot">
          <div className="foot-left">
            {!isValid && Object.keys(touched).length > 0 &&
            <span className="foot-error"><Icon.Alert /> {Object.values(errors).includes(requiredText) ? t("form.fillRequired") : t("form.checkInput")}</span>
            }
            {serverError &&
              <span className="foot-error" role="alert">
                <Icon.Alert /> {t("form.saveFailed", { message: serverError.serverMessage || t("detail.tryAgainLater") })}
              </span>}
          </div>
          <div className="foot-right">
            <button className="btn" onClick={onCancel} disabled={submitting}>{t("common.cancel")}</button>
            <button className="btn" onClick={submit} disabled={!isValid || submitting} style={(!isValid || submitting) ? { opacity: 0.5 } : null}>
              {submitting ? t("common.saving") : t("form.saveAndContinue")}
            </button>
            <button className="btn btn-primary" onClick={submit} disabled={!isValid || submitting} style={(!isValid || submitting) ? { opacity: 0.5 } : null}>
              <Icon.Check /> {submitting ? t("common.saving") : t("form.save")}
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
  const t = useT();
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
            aria-label={t("form.openList")}
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
