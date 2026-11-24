// Domain contracts for the Impfdossier mobile app (aligned with OpenAPI)

/** A single administered vaccination dose matching VaccinationDto from the OpenAPI spec. */
export interface VaccinationReason {
  readonly code: string;
  readonly display: string;
}

export interface Vaccination {
  readonly id: string;
  readonly disease: string;      // targetDisease
  readonly vaccine: string;      // vaccineName
  readonly vaccineCode: string;  // vaccineCode
  readonly date: string;         // vaccinationDate
  readonly dose: string;         // doseSequence
  readonly doseNumber?: string;   // doseNumber
  readonly seriesDoses?: string;  // seriesDoses
  readonly vaccinationReason?: VaccinationReason; // vaccinationReason structured
  readonly manufacturer: string;
  readonly batch: string;        // lotNumber
  readonly route: string;        // administrationRoute
  readonly site: string;         // siteOfAdministration
  readonly season?: string;      // season (optional)
}

/** The complete patient dossier matching PatientDossierDto from the OpenAPI spec. */
export interface PatientDossier {
  readonly firstName: string;
  readonly lastName: string;
  readonly age: number;
  readonly gender: string;
  readonly vaccinations: readonly Vaccination[];
}

/** Vaccinations for one disease, sorted oldest-first, with the most recent date precomputed. */
export interface DiseaseGroup {
  readonly disease: string;
  readonly entries: readonly Vaccination[];
  readonly latest: string;
}

