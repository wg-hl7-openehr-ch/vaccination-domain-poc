// Root app — routes between PatientList, PatientDetail, VaccinationForm

function App() {
  const t = useT();
  const [route, setRoute] = useState({ name: "list" });
  const [showForm, setShowForm] = useState(false);
  const [justAdded, setJustAdded] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    setLoading(true);
    DataService.fetchPatients()
      .then((apiPatients) => {
        window.AppData.patients = apiPatients.map((p, i) => DataService.transformPatient(p, i));
        window.AppData.vaccinations = {};
        setLoading(false);
      })
      .catch((err) => {
        console.warn('API nicht erreichbar, verwende Fallback-Daten:', err);
        DataService.loadMockFallback();
        setLoading(false);
      });
  }, []);

  const crumbs = useMemo(() => {
    if (route.name === "list") return [{ key: "list", label: t("app.breadcrumbPatients") }];
    if (route.name === "detail") {
      const p = (window.AppData.patients || []).find(x => x.id === route.patientId);
      return [
        { key: "list", label: t("app.breadcrumbPatients") },
        { key: "detail", label: p ? p.lastName + ', ' + p.firstName : t("app.breadcrumbPatient") },
      ];
    }
    return [];
  }, [route, loading, t]);

  const onCrumb = (c) => {
    if (c.key === "list") setRoute({ name: "list" });
  };

  const openPatient = (id) => {
    setJustAdded(null);
    setRoute({ name: "detail", patientId: id });
  };

  const handleVaccinationCreated = (record) => {
    setShowForm(false);
    setJustAdded({ ...record, _addedId: record.id });
    setTimeout(() => setJustAdded(j => j && { ...j, _addedId: null }), 4500);
  };

  if (loading) {
    return (
      <div className="app">
        <TopBar crumbs={[]} onCrumb={() => {}} />
        <main className="page" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '60vh' }}>
          <div style={{ textAlign: 'center', color: 'var(--text-3)' }}>
            <div className="spinner" style={{ width: 32, height: 32, border: '3px solid var(--border)', borderTopColor: 'var(--accent)', borderRadius: '50%', animation: 'spin .6s linear infinite', margin: '0 auto 1rem' }} />
            <div>{t("app.loadingPatients")}</div>
          </div>
        </main>
      </div>
    );
  }

  if (error) {
    return (
      <div className="app">
        <TopBar crumbs={[]} onCrumb={() => {}} />
        <main className="page" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '60vh' }}>
          <div style={{ textAlign: 'center', color: 'var(--danger)' }}>
            <div style={{ fontSize: '1.25rem', fontWeight: 600, marginBottom: '.5rem' }}>{t("app.connectionError")}</div>
            <div>{error}</div>
          </div>
        </main>
      </div>
    );
  }

  return (
    <div className="app">
      <TopBar crumbs={crumbs} onCrumb={onCrumb} />

      {route.name === "list" && (
        <PatientList onOpenPatient={openPatient} />
      )}

      {route.name === "detail" && (
        <PatientDetail
          patientId={route.patientId}
          onBack={() => setRoute({ name: "list" })}
          onAddVaccination={() => setShowForm(true)}
          justAdded={justAdded}
          onVaccinationCreated={handleVaccinationCreated}
        />
      )}

      {showForm && route.name === "detail" && (
        <VaccinationForm
          patient={(window.AppData.patients || []).find(p => p.id === route.patientId)}
          onCancel={() => setShowForm(false)}
          onVaccinationCreated={handleVaccinationCreated}
        />
      )}
    </div>
  );
}

ReactDOM.createRoot(document.getElementById("root")).render(<App />);
