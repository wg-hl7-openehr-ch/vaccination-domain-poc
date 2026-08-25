// API data service — reads auth from sessionStorage, calls bff-producer via nginx proxy.
// Exports window.DataService.

(function () {

function getAuth() {
  try {
    return JSON.parse(sessionStorage.getItem('auth'));
  } catch { return null; }
}

function authHeaders() {
  const auth = getAuth();
  return auth?.token ? { 'Authorization': 'Bearer ' + auth.token } : {};
}

async function fetchPatients() {
  const res = await fetch('/api/bff-producer/patients', {
    headers: { ...authHeaders() },
  });
  if (!res.ok) throw new Error('Fehler beim Laden der Patienten');
  return res.json();
}

async function fetchVaccinations(personId) {
  const res = await fetch('/api/bff-producer/vaccinations?personId=' + encodeURIComponent(personId), {
    headers: { ...authHeaders() },
  });
  if (!res.ok) throw new Error('Fehler beim Laden der Impfungen');
  return res.json();
}

async function exportVaccinationRecord(personId, format) {
  const url = '/api/bff-producer/vaccinations/export?personId=' + encodeURIComponent(personId) + '&format=' + encodeURIComponent(format);
  const res = await fetch(url, {
    headers: { ...authHeaders() },
  });
  if (!res.ok) throw new Error('Fehler beim Exportieren des Impfausweises');
  return res;
}

async function createImmunization(personId, data) {
  const res = await fetch('/api/bff-producer/immunizations?personId=' + encodeURIComponent(personId), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders(),
    },
    body: JSON.stringify(data),
  });
  if (!res.ok) throw new Error('Fehler beim Erfassen der Impfung');
  return res.json();
}

async function fetchVaccineCodes() {
  const res = await fetch('/api/bff-producer/valuesets/vaccines', {
    headers: { ...authHeaders() },
  });
  if (!res.ok) throw new Error('Fehler beim Laden der Impfstoff-Codes');
  return res.json();
}

function transformPatient(apiPatient, index) {
  const genderMap = { MÄNNLICH: 'M', WEIBLICH: 'F', DIVERS: 'D' };
  const addr = apiPatient.address;
  const addrStr = addr ? [addr.street, addr.zipCode, addr.city].filter(Boolean).join(', ') : '';
  return {
    id: apiPatient.id || 'P-' + String(index + 1).padStart(4, '0'),
    firstName: apiPatient.firstName || '',
    lastName: apiPatient.lastName || '',
    dob: apiPatient.birthDate || '',
    sex: genderMap[apiPatient.gender] || 'M',
    address: addrStr,
    email: apiPatient.email || '',
    phone: apiPatient.phoneNumber || '',
    ahv: apiPatient.ahvNumber || '',
  };
}

function transformVaccination(apiImm) {
  const staticReasons = {
    "3fa85f64-5717-4562-b3fc-2c963f66afa6": { code: "373068000", display: "Not known", swissLabel: "Grundimmunisierung" },
    "7b921e12-3456-7890-abcd-ef1234567890": { code: "373068000", display: "Not known", swissLabel: "Grundimmunisierung" },
    "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d": { code: "Booster", display: "Booster", swissLabel: "Auffrischimpfung (Booster)" },
    "f8e7d6c5-b4a3-9281-7065-5443322110aa": { code: "1237021005", display: "At increased risk of exposure to European tick-borne encephalitis virus", swissLabel: "Risikogruppe: FSME-Exposition (Endemiegebiet)" },
    "00000000-1111-2222-3333-444455556666": { code: "1237028004", display: "At increased risk of exposure to Influenza virus", swissLabel: "Risikogruppe: Erhöhtes Influenza-Expositionsrisiko" }
  };

  return {
    id: apiImm.id,
    vaccine: apiImm.vaccineName || '',
    vaccineCode: apiImm.vaccineCode || '',
    disease: inferDisease(apiImm.vaccineName || ''),
    date: apiImm.vaccinationDate || '',
    dose: apiImm.doseSequence || '',
    doseNumber: apiImm.doseNumber || undefined,
    seriesDoses: apiImm.seriesDoses || undefined,
    batch: apiImm.lotNumber || '',
    manufacturer: apiImm.manufacturer || '',
    route: apiImm.administrationRoute || '',
    site: apiImm.siteOfAdministration || '',
    practitioner: apiImm.practitioner || null,
    vaccinationReason: apiImm.vaccinationReason || staticReasons[apiImm.id] || null,
  };
}

function routeToApi(route) {
  const map = { 'i.m.': 'IM', 's.c.': 'SC', 'i.d.': 'ID', 'oral': 'ORAL', 'nasal': 'NASAL' };
  return map[route] || 'IM';
}

function inferDisease(vaccine) {
  const v = (vaccine || '').toLowerCase();
  if (v.includes('boostrix polio') || v.includes('infanrix-ipv') || v.includes('pentavac')) return 'Diphtherie, Tetanus, Pertussis, Polio';
  if (v.includes('infanrix hexa')) return 'Diphtherie, Tetanus, Pertussis, Polio, Hib, Hepatitis B';
  if (v.includes('boostrix')) return 'Diphtherie, Tetanus, Pertussis';
  if (v.includes('td-pur')) return 'Diphtherie, Tetanus';
  if (v.includes('priorix') || v.includes('mmr')) return 'Masern, Mumps, Röteln';
  if (v.includes('gardasil')) return 'HPV';
  if (v.includes('fsme') || v.includes('encepur')) return 'FSME';
  if (v.includes('engerix')) return 'Hepatitis B';
  if (v.includes('havrix')) return 'Hepatitis A';
  if (v.includes('twinrix')) return 'Hepatitis A + B';
  if (v.includes('comirnaty') || v.includes('spikevax') || v.includes('covid')) return 'COVID-19';
  if (v.includes('influvac') || v.includes('fluarix') || v.includes('efluelda')) return 'Influenza (saisonal)';
  if (v.includes('prevenar') || v.includes('pneumo')) return 'Pneumokokken';
  if (v.includes('menveo') || v.includes('nimenrix')) return 'Meningokokken ACWY';
  if (v.includes('rabipur')) return 'Tollwut';
  if (v.includes('stamaril')) return 'Gelbfieber';
  if (v.includes('shingrix')) return 'Herpes Zoster';
  if (v.includes('varilrix')) return 'Varizellen';
  return 'Sonstige';
}


function loadMockFallback() {
  window.AppData.patients = [
    { id: 'P-0001', firstName: 'Demo', lastName: 'Patient', dob: '1990-01-01', sex: 'M', address: 'Musterstrasse 1, 8000 Zürich', email: 'demo@example.ch', phone: '+41 00 000 00 00', ahv: '756.0000.0000.00' },
  ];
  window.AppData.vaccinations = {};
}

window.DataService = {
  getAuth,
  fetchPatients,
  fetchVaccinations,
  createImmunization,
  fetchVaccineCodes,
  exportVaccinationRecord,
  transformPatient,
  transformVaccination,
  routeToApi,
  inferDisease,
  loadMockFallback,
};

})();
