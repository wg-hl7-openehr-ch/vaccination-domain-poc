"""Patient mock data for the consumer BFF.

Consulted by `_ensure_patient` in app.py when an unknown patient ID is
first requested: known iam-mock user IDs get persisted with realistic
demographics, unknown IDs fall back to a minimal stub.

IDs are kept in sync with USERS (role == "patient") in
services/iam-mock/server.py."""

from __future__ import annotations

from typing import Any

PATIENTS: dict[str, dict[str, Any]] = {
    "00000000-0000-0000-0000-000000000001": {
        "firstName": "Anna",
        "lastName": "Schmid",
        "gender": "female",
        "birthDate": "1984-07-22",
    },
    "00000000-0000-0000-0000-000000000002": {
        "firstName": "Simon",
        "lastName": "Koller",
        "gender": "male",
        "birthDate": "1998-03-15",
    },
    "00000000-0000-0000-0000-000000000003": {
        "firstName": "Lara",
        "lastName": "Müller",
        "gender": "female",
        "birthDate": "2019-04-10",
    },
    "00000000-0000-0000-0000-000000000004": {
        "firstName": "Heinrich",
        "lastName": "Brunner",
        "gender": "male",
        "birthDate": "1953-11-08",
    },
}


def patient_resource(patient_id: str) -> dict[str, Any]:
    """Return a FHIR Patient resource for the given ID.

    If the ID matches a known iam-mock user, the resource carries full
    demographic mock data; otherwise it is a minimal stub."""
    p = PATIENTS.get(patient_id)
    if p is None:
        return {"resourceType": "Patient", "id": patient_id}
    return {
        "resourceType": "Patient",
        "id": patient_id,
        "name": [{"family": p["lastName"], "given": [p["firstName"]]}],
        "gender": p["gender"],
        "birthDate": p["birthDate"],
    }
