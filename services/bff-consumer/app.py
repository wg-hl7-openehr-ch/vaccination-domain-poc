import os
from datetime import date
from typing import Any

import httpx
from fastapi import FastAPI, HTTPException, Query

from seed import patient_resource
from vaccinations_read_service import (
    FhirClient as VaccinationsFhirClient,
    VaccinationDto,
    VaccinationsReadService,
)

FHIR_BASE = os.getenv(
    "FHIR_BASE_URL",
    "http://fhir-server-1:9111/ch-vacd-api-reference-server/fhir",
)
TIMEOUT = float(os.getenv("FHIR_TIMEOUT_SECONDS", "10"))

GENDER_DE = {
    "male": "männlich",
    "female": "weiblich",
    "other": "andere",
    "unknown": "unbekannt",
}

app = FastAPI(title="CH VACD Consumer BFF", version="0.1.0")
fhir = httpx.AsyncClient(
    timeout=TIMEOUT,
    headers={"Accept": "application/fhir+json"},
)
vaccinations_service = VaccinationsReadService(VaccinationsFhirClient(fhir, FHIR_BASE))


@app.get("/healthz")
async def healthz() -> dict[str, Any]:
    try:
        r = await fhir.get(f"{FHIR_BASE}/metadata")
        return {"ok": r.status_code == 200, "fhirStatus": r.status_code}
    except Exception as e:
        return {"ok": False, "error": str(e)}


@app.get("/vaccinations/")
async def get_vaccinations(patient_id: str = Query(..., alias="personId")) -> list[VaccinationDto]:
    try:
        return await vaccinations_service.get_vaccination_list(patient_id)
    except httpx.HTTPError as e:
        raise HTTPException(status_code=502, detail=f"FHIR error: {e}") from e


@app.get("/patients/{patient_id}")
async def get_dossier(patient_id: str) -> dict[str, Any]:
    await _ensure_patient(patient_id)
    patient = await _get_patient(patient_id)
    immunizations = await _get_immunizations(patient_id)
    return {
        "firstName": _first_name(patient),
        "lastName": _last_name(patient),
        "age": _calc_age(patient.get("birthDate")) or 0,
        "gender": GENDER_DE.get(patient.get("gender") or "unknown", "unbekannt"),
        "vaccinations": [
            v for imm in immunizations for v in _expand_vaccinations(imm)
        ],
    }


async def _ensure_patient(patient_id: str) -> bool:
    """Return True if the patient had to be created, False if it already existed."""
    # The CH VACD reference PatientProvider returns a synthetic stub for any
    # GET /Patient/{id} (never 404), so we check via Search which only yields
    # actually-stored Patients.
    r = await fhir.get(f"{FHIR_BASE}/Patient", params={"_count": "1000"})
    if r.status_code != 200:
        raise HTTPException(
            status_code=502,
            detail=f"Patient search failed ({r.status_code}): {r.text[:200]}",
        )
    stored_ids = {
        (e.get("resource") or {}).get("id")
        for e in (r.json().get("entry") or [])
    }
    if patient_id in stored_ids:
        return False
    create = await fhir.put(
        f"{FHIR_BASE}/Patient/{patient_id}",
        json=patient_resource(patient_id),
        headers={"Content-Type": "application/fhir+json"},
    )
    if create.status_code not in (200, 201):
        raise HTTPException(
            status_code=502,
            detail=f"Patient create failed ({create.status_code}): {create.text[:200]}",
        )
    return True


async def _get_patient(patient_id: str) -> dict[str, Any]:
    r = await fhir.get(f"{FHIR_BASE}/Patient/{patient_id}")
    if r.status_code != 200:
        raise HTTPException(
            status_code=502,
            detail=f"Patient GET failed ({r.status_code}): {r.text[:200]}",
        )
    return r.json()


async def _get_immunizations(patient_id: str) -> list[dict[str, Any]]:
    # The CH VACD ImmunizationProvider ignores the `patient` search param and
    # always returns the whole store, so we filter client-side on patient.reference.
    r = await fhir.get(
        f"{FHIR_BASE}/Immunization",
        params={"patient": patient_id, "_count": "1000"},
    )
    if r.status_code != 200:
        raise HTTPException(
            status_code=502,
            detail=f"Immunization search failed ({r.status_code}): {r.text[:200]}",
        )
    wanted = f"Patient/{patient_id}"
    return [
        res
        for e in (r.json().get("entry") or [])
        if (res := e.get("resource"))
        and (res.get("patient") or {}).get("reference") == wanted
    ]


def _first_name(patient: dict[str, Any]) -> str:
    names = patient.get("name") or []
    if not names:
        return ""
    given = names[0].get("given") or []
    return given[0] if given else ""


def _last_name(patient: dict[str, Any]) -> str:
    names = patient.get("name") or []
    if not names:
        return ""
    return names[0].get("family") or ""


def _calc_age(birth_date_str: str | None) -> int | None:
    # FHIR `date` may be YYYY, YYYY-MM, or YYYY-MM-DD.
    if not birth_date_str:
        return None
    parts = birth_date_str.split("-")
    try:
        y = int(parts[0])
        m = int(parts[1]) if len(parts) > 1 else 1
        d = int(parts[2]) if len(parts) > 2 else 1
    except ValueError:
        return None
    today = date.today()
    return max(today.year - y - ((today.month, today.day) < (m, d)), 0)


def _expand_vaccinations(imm: dict[str, Any]) -> list[dict[str, Any]]:
    vaccine = imm.get("vaccineCode") or {}
    vaccine_coding = (vaccine.get("coding") or [{}])[0]
    vaccine_name = vaccine_coding.get("display") or vaccine.get("text") or ""

    proto = (imm.get("protocolApplied") or [{}])[0]
    dose_num = proto.get("doseNumberPositiveInt") or proto.get("doseNumberString")
    series = proto.get("seriesDosesPositiveInt") or proto.get("seriesDosesString")
    if dose_num is not None and series is not None:
        dose_sequence = f"{dose_num}/{series}"
    elif dose_num is not None:
        dose_sequence = str(dose_num)
    else:
        dose_sequence = ""

    vaccination_date = (imm.get("occurrenceDateTime") or "")[:10]
    manufacturer = (imm.get("manufacturer") or {}).get("display") or ""
    lot_number = imm.get("lotNumber") or ""

    route = imm.get("route") or {}
    route_coding = (route.get("coding") or [{}])[0]
    administration_route = route_coding.get("display") or route.get("text") or ""

    site = imm.get("site") or {}
    site_coding = (site.get("coding") or [{}])[0]
    site_of_administration = site_coding.get("display") or site.get("text") or ""

    target_diseases: list[str] = []
    for td in proto.get("targetDisease") or []:
        coding = (td.get("coding") or [{}])[0]
        display = coding.get("display") or td.get("text")
        if display:
            target_diseases.append(display)
    if not target_diseases:
        target_diseases = [vaccine_name or ""]

    return [
        {
            "targetDisease": disease,
            "vaccineName": vaccine_name,
            "vaccineCode": vaccine_coding.get("code") or "",
            "season": "",
            "doseSequence": dose_sequence,
            "vaccinationDate": vaccination_date,
            "manufacturer": manufacturer,
            "lotNumber": lot_number,
            "administrationRoute": administration_route,
            "siteOfAdministration": site_of_administration,
        }
        for disease in target_diseases
    ]
