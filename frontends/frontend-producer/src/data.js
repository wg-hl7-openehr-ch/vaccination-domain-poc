const doctor = {
  name: "Dr. med. Sarah Müller",
  title: "Fachärztin Allgemeine Innere Medizin FMH",
  gln: "7601000123456",
  praxis: "Hausarztpraxis Bahnhofstrasse",
  address: "Bahnhofstrasse 47, 8001 Zürich",
  initials: "SM",
};

window.AppData = { doctor };

// Catalog used in form dropdowns
const vaccineCatalog = [
  { combined: "http://fhir.ch/ig/ch-vacd/CodeSystem/ch-vacd-swissmedic-cs|681|Boostrix Polio", display: "Boostrix"},
  { combined: "http://fhir.ch/ig/ch-vacd/CodeSystem/ch-vacd-swissmedic-cs|637|Boostrix", display: "Boostrix Polio"}
];

const manufacturers = [
  "Bavarian Nordic",
  "GlaxoSmithKline",
  "Moderna",
  "MSD",
  "Pfizer",
  "Pfizer-BioNTech",
  "Sanofi",
  "Viatris",
];

const routes = [
  { value: "i.m.", label: "i.m. (intramuskulär)" },
  { value: "s.c.", label: "s.c. (subkutan)" },
  { value: "i.d.", label: "i.d. (intradermal)" },
  { value: "oral", label: "oral" },
  { value: "nasal", label: "nasal" },
];

const sites = [
  "Oberarm links (M. deltoideus)",
  "Oberarm rechts (M. deltoideus)",
  "Oberschenkel links (M. vastus lateralis)",
  "Oberschenkel rechts (M. vastus lateralis)",
  "Gesäss links",
  "Gesäss rechts",
];

const reasons = [
  { code: "373068000", display: "Not known", swissLabel: "Grundimmunisierung" },
  { code: "Booster", display: "Booster", swissLabel: "Auffrischimpfung (Booster)" },
  { code: "Nachholimpfung", display: "Nachholimpfung", swissLabel: "Nachholimpfung" },
  { code: "14747002", display: "Elective immunization for international travel", swissLabel: "Reiseimpfung" },
  { code: "Postexpositionsprophylaxe", display: "Postexpositionsprophylaxe", swissLabel: "Postexpositionsprophylaxe" },
  
  // Offizielle Schweizer Risikocodes aus dem Risks ValueSet (CH-VACD 6.0.0):
  { code: "223366009", display: "Healthcare professional", swissLabel: "Risikogruppe: Medizinisches Fachpersonal (beruflich)" },
  { code: "1237021005", display: "At increased risk of exposure to European tick-borne encephalitis virus", swissLabel: "Risikogruppe: FSME-Exposition (Endemiegebiet)" },
  { code: "1237028004", display: "At increased risk of exposure to Influenza virus", swissLabel: "Risikogruppe: Erhöhtes Influenza-Expositionsrisiko" },
  { code: "870577009", display: "At increased risk of exposure to SARS-CoV-2", swissLabel: "Risikogruppe: Erhöhtes COVID-19-Expositionsrisiko" },
  { code: "77386006", display: "Patient currently pregnant", swissLabel: "Risikogruppe: Schwangere Patientin" },
  { code: "226034001", display: "Injecting drug user", swissLabel: "Risikogruppe: IV-Drogenkonsum" },
  { code: "56265001", display: "Heart disease", swissLabel: "Risikogruppe: Chronische Herzerkrankung (medizinisch)" },
  { code: "19829001", display: "Lung disorder", swissLabel: "Risikogruppe: Chronische Atemwegserkrankung (medizinisch)" },
  { code: "75934005", display: "Metabolic disease", swissLabel: "Risikogruppe: Stoffwechselerkrankung (z. B. Diabetes)" },
  { code: "90708001", display: "Kidney disease", swissLabel: "Risikogruppe: Chronische Nierenerkrankung" },
  { code: "414029004", display: "Disorder of immune function (disorder)", swissLabel: "Risikogruppe: Immundefizienz / Immungeschwächt" }
];

window.AppData = { doctor, vaccineCatalog, manufacturers, routes, sites, reasons };
