// Screen 2 — Patient vaccination history (Swiss Impfausweis style)

function PatientDetail({ patientId, onBack, onAddVaccination, justAdded, onVaccinationCreated, onExportVaccination }) {
  const { patients } = window.AppData;
  const patient = (patients || []).find((p) => p.id === patientId);
  const [records, setRecords] = useState([]);
  const [vaxLoading, setVaxLoading] = useState(true);
  const [importDone, setImportDone] = useState(false);
  const [errorMsg, setErrorMsg] = useState(null);

  const showError = (msg) => setErrorMsg(msg);

  const loadVaccinations = () => {
    setVaxLoading(true);
    DataService.fetchVaccinations(patientId)
      .then((apiImms) => {
        setRecords(apiImms.map((r) => DataService.transformVaccination(r)));
        setVaxLoading(false);
      })
      .catch((err) => {
        console.warn('Immunisierungs-API nicht erreichbar:', err);
        const fallback = (window.AppData.vaccinations || {})[patientId] || [];
        setRecords(fallback);
        setVaxLoading(false);
      });
  };

  useEffect(() => {
    loadVaccinations();
  }, [patientId]);

  // Sync browser back button with the in-app back navigation
  useEffect(() => {
    history.pushState({ patientDetail: patientId }, '');
    const handlePop = () => onBack();
    window.addEventListener('popstate', handlePop);
    return () => window.removeEventListener('popstate', handlePop);
  }, [patientId]);

  // Wenn eine neue Impfung via onVaccinationCreated hinzugefügt wurde, die Liste neu laden
  useEffect(() => {
    if (justAdded && justAdded.id) {
      loadVaccinations();
    }
  }, [justAdded]);

  useEffect(() => {
    if (!importDone) return;
    const t = setTimeout(() => setImportDone(false), 4000);
    return () => clearTimeout(t);
  }, [importDone]);

  useEffect(() => {
    if (!errorMsg) return;
    const t = setTimeout(() => setErrorMsg(null), 5000);
    return () => clearTimeout(t);
  }, [errorMsg]);

  const handleExportVaccination = async () => {
    try {
      const res = await DataService.exportVaccinationRecord(patientId, 'json');
      const jsonContent = await res.json();
      const blob = new Blob([JSON.stringify(jsonContent, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `VaccinationRecord-${patient.lastName}-${patient.firstName}.json`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      if (onExportVaccination) onExportVaccination(jsonContent);
    } catch (err) {
      console.error('Export fehlgeschlagen:', err);
      showError('Export fehlgeschlagen: ' + (err.message || err));
    }
  };

  const handleImportVaccination = () => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.json,.xml';
    input.onchange = async (e) => {
      const file = e.target.files[0];
      if (!file) return;
      try {
        await DataService.importVaccinations(patientId, file);
        loadVaccinations();
        setImportDone(true);
      } catch (err) {
        console.error('Import fehlgeschlagen:', err);
        showError('Import fehlgeschlagen: ' + (err.message || err));
      }
    };
    input.click();
  };

  if (!patient) return <div className="page">Patient:in nicht gefunden.</div>;

  // Group by target disease, sort chronologically within group.
  const groups = useMemo(() => {
    const map = new Map();
    for (const r of records) {
      if (!map.has(r.disease)) map.set(r.disease, []);
      map.get(r.disease).push(r);
    }
    for (const arr of map.values()) arr.sort((a, b) => a.date.localeCompare(b.date));
    const groupArr = [...map.entries()].map(([disease, items]) => ({
      disease,
      items,
      latest: items[items.length - 1].date
    }));
    groupArr.sort((a, b) => b.latest.localeCompare(a.latest));
    return groupArr;
  }, [records]);

  const stats = useMemo(() => {
    const total = records.length;
    const diseases = new Set(records.map((r) => r.disease)).size;
    const recent = records.slice().sort((a, b) => b.date.localeCompare(a.date))[0];
    return { total, diseases, recent };
  }, [records]);

  return (
    <main className="page">
      <button className="back-link" onClick={onBack}>
        <Icon.Back /> Zurück zur Patient:innenliste
      </button>

      {errorMsg &&
      <div className="toast-error">
          <div className="toast-icon"><Icon.Close /></div>
          <div>
            <div className="toast-title">Fehler</div>
            <div className="toast-sub">{errorMsg}</div>
          </div>
        </div>
      }

      {importDone &&
      <div className="toast-success">
          <div className="toast-icon"><Icon.Check /></div>
          <div>
            <div className="toast-title">Impfausweis importiert</div>
            <div className="toast-sub">Die Impfungen wurden erfolgreich importiert.</div>
          </div>
        </div>
      }

      {justAdded && justAdded._addedId &&
      <div className="toast-success">
          <div className="toast-icon"><Icon.Check /></div>
          <div>
            <div className="toast-title">Impfung erfasst</div>
            <div className="toast-sub">
              {justAdded.vaccine} · {formatDate(justAdded.date, { short: true })} wurde dem Dossier hinzugefügt.
            </div>
          </div>
        </div>
      }

      <section className="patient-hero">
        <div className="patient-hero-left">
          <PatientAvatar patient={patient} size={64} />
          <div>
            <div className="patient-name-line">
              <h1 className="patient-name">{patient.lastName}, {patient.firstName}</h1>
            </div>
            <div className="patient-meta">
              <span><Icon.Cake /> {formatDate(patient.dob, { short: true })} <em className="muted">·</em> {patient.age ?? calcAge(patient.dob)} Jahre <em className="muted">·</em> {patient.sex === "F" ? "weiblich" : "männlich"}</span>
              <span><Icon.Pin /> {patient.address}</span>
              <span><Icon.Mail /> {patient.email}</span>
              <span><Icon.Phone /> <span className="tnum">{patient.phone}</span></span>
              <span><Icon.User /> AHV <span className="mono">{patient.ahv}</span></span>
            </div>
          </div>
        </div>

        <div className="patient-hero-right">
          <div className="hero-stat-naked">
            <div className="hero-stat-value tnum">{stats.total}</div>
            <div className="hero-stat-label">Impfungen total</div>
          </div>
        </div>
      </section>

      <div className="detail-actions">
        <div className="detail-actions-title">
          <h2 className="section-title">Impfausweis</h2>
        </div>
        <div className="detail-actions-right">
          <button className="btn btn-primary" onClick={handleImportVaccination} disabled={records.length > 0}><Icon.Upload /> Impfausweis importieren</button>
          <button className="btn btn-primary" onClick={handleExportVaccination} disabled={records.length === 0}><Icon.Download /> Impfausweis exportieren</button>
          <button className="btn btn-primary" onClick={onAddVaccination}><Icon.Plus /> Neue Impfung erfassen</button>
        </div>
      </div>

      {vaxLoading &&
      <div className="card" style={{ padding: 32, textAlign: 'center', color: 'var(--text-3)' }}>
          Impfungen werden geladen …
        </div>
      }

      {!vaxLoading &&
      <div className="vax-groups">
          {groups.map((g) =>
          <VaccinationGroup key={g.disease} group={g} justAddedId={justAdded?._addedId} />
          )}
          {groups.length === 0 &&
          <div className="card" style={{ padding: 32, textAlign: 'center', color: 'var(--text-3)' }}>
              Noch keine Impfungen erfasst. Klicke auf „Neue Impfung erfassen".
            </div>
          }
        </div>
      }
    </main>);

}

function VaccinationGroup({ group, justAddedId }) {
  return (
    <section className="vax-group card">
      <header className="vax-group-head">
        <div className="vax-group-title-wrap">
          <div className="vax-target-icon"><Icon.Syringe /></div>
          <div>
            <div className="vax-group-title">{group.disease}</div>
            <div className="vax-group-meta">
              {group.items.length} {group.items.length === 1 ? "Eintrag" : "Einträge"}
              {" · "}letzte Dosis {formatDate(group.latest, { short: true })}
            </div>
          </div>
        </div>
        <span className="badge badge-accent"><Icon.Check /> Geschützt</span>
      </header>

      <ol className="vax-timeline">
        {group.items.map((r, i) => {
          const doseLabel = r.doseNumber === 'Booster'
            ? 'Booster'
            : (r.doseNumber && r.seriesDoses)
              ? `Dosis ${r.doseNumber} / ${r.seriesDoses}`
              : r.dose;
          return (
            <li key={r.id} className={"vax-entry" + (justAddedId === r.id ? " is-new" : "")}>
              <div className="vax-tl-dot" aria-hidden="true" />
              <div className="vax-entry-card">
                <div className="vax-entry-main">
                  <div className="vax-entry-headline">
                    <span className="vax-vaccine">{r.vaccine}</span>
                    <span className="vax-dose-pill">{doseLabel}</span>
                    {justAddedId === r.id && <span className="badge badge-success">Neu</span>}
                  </div>
                  <div className="vax-entry-meta-row">
                    <span><b className="tnum">{formatDate(r.date)}</b></span>
                    <span className="dot">·</span>
                    <span>{r.manufacturer}</span>
                    <span className="dot">·</span>
                    <span>Charge <span className="mono">{r.batch}</span></span>
                  </div>
                  <div className="vax-entry-meta-row sub">
                    <span>{r.route}</span>
                    <span className="dot">·</span>
                    <span>{r.site}</span>
                    {r.note && <><span className="dot">·</span><span>{r.note}</span></>}
                  </div>
                  {r.vaccinationReason && (
                    <div className="vax-entry-meta-row reason" style={{ fontStyle: 'italic', color: 'var(--primary)', marginTop: 4 }}>
                      Impfgrund: {r.vaccinationReason.swissLabel || r.vaccinationReason.display || r.vaccinationReason}
                    </div>
                  )}
                </div>
                <div className="vax-entry-side">
                  <div className="vax-entry-doc">{r.practitioner?.doctorName || 'Dr. S. Müller'}</div>
                  <div className="vax-entry-gln mono">{r.practitioner ? 'GLN ' + r.practitioner.gln?.slice(0, 4) + '…' + r.practitioner.gln?.slice(-4) : 'GLN 7601…3456'}</div>
                </div>
              </div>
            </li>
          );
        })}
      </ol>
    </section>);

}

window.PatientDetail = PatientDetail;
