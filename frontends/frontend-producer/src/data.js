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
  { value: "i.m.", labelKey: "route.im" },
  { value: "s.c.", labelKey: "route.sc" },
  { value: "i.d.", labelKey: "route.id" },
  { value: "oral", labelKey: "route.oral" },
  { value: "nasal", labelKey: "route.nasal" },
];

const sites = [
  { value: "Oberarm links (M. deltoideus)", labelKey: "site.upperArmLeft" },
  { value: "Oberarm rechts (M. deltoideus)", labelKey: "site.upperArmRight" },
  { value: "Oberschenkel links (M. vastus lateralis)", labelKey: "site.thighLeft" },
  { value: "Oberschenkel rechts (M. vastus lateralis)", labelKey: "site.thighRight" },
  { value: "Gesäss links", labelKey: "site.buttockLeft" },
  { value: "Gesäss rechts", labelKey: "site.buttockRight" },
];

const units = [
  { value: "ml", labelKey: "unit.ml" },
  { value: "Dosis", labelKey: "unit.dose" },
  { value: "Tropfen", labelKey: "unit.drops" },
];

const reasons = [
  { code: "33879002", display: "Administration of vaccine to produce active immunity (procedure)" },
  { code: "359953009", display: "Booster vaccination (procedure)" },
  { code: "308432007", display: "Immunization recall (procedure)" },
  { code: "14747002", display: "Elective immunization for international travel (procedure)" },
  { code: "409516001", display: "Post-exposure prophylaxis (procedure)" },
  
  // Offizielle Schweizer Risikocodes aus dem Risks ValueSet (CH-VACD 6.0.0):
  { code: "223366009", display: "Healthcare professional" },
  { code: "1237021005", display: "At increased risk of exposure to European tick-borne encephalitis virus" },
  { code: "1237028004", display: "At increased risk of exposure to Influenza virus" },
  { code: "870577009", display: "At increased risk of exposure to SARS-CoV-2" },
  { code: "77386006", display: "Patient currently pregnant" },
  { code: "226034001", display: "Injecting drug user" },
  { code: "56265001", display: "Heart disease" },
  { code: "19829001", display: "Lung disorder" },
  { code: "75934005", display: "Metabolic disease" },
  { code: "90708001", display: "Kidney disease" },
  { code: "414029004", display: "Disorder of immune function (disorder)" }
];

window.AppData = { doctor, vaccineCatalog, manufacturers, routes, sites, units, reasons };
