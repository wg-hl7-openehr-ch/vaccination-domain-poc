# CLAUDE.md

Orientation for Claude Code sessions in this repo.

- **What this repo is:** a dev harness for the Vaccination Domain POC
  `README.md` has the overview, service table, and
  architecture patterns.

## Scope

The harness = `.devcontainer/`, `docker-compose.yml`, `services/` source
for locally-built containers, `examples/` (canonical CH VACD test
Bundles), and `docs/`. The **platform layer** that mediates between
the CH VACD FHIR API and the openEHR Clinical Data Repository is what
hackathon participants build — it is deliberately not pre-decided by the
harness. The default `docker compose up` starts only the backing services;
participant platform layers do **not** belong in the default service set.

The repo also contains a **Platform Business Logic Example** at
`services/platform-business-logic-example/`. This is a **harness
smoke-test** — it was built to validate that the backing services work
end-to-end, not to serve as a starting point or reference architecture
for participants. Teams should design their own platform layer from
scratch; the example exists only to prove the plumbing works. It is
gated behind the `example` compose profile (`docker compose --profile
example up --wait`) so it never starts by default.

