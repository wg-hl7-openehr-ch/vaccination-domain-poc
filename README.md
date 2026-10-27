# Vaccination Domain POC — Digital Immunization Record (VACD)

Dev harness for the Vaccination Domain POC 
Clone, open in VS Code with the
Dev Containers extension, `docker compose up`, and you have the
backing services the challenge revolves around.

## Quick start

1. Open the repo in VS Code and choose **"Reopen in Container"**. The
   dev container ships Java 25, Maven/Gradle, Node 22, Python 3.13,
   Kotlin 2.3.21, Docker, `gh`, and the 
   
   Claude/Gemini/Codex CLIs.
2. Inside the container terminal:
   ```sh
   docker compose up --wait
   ```
   This builds the FHIR server from source (multi-stage Docker build, no
   local Maven needed) and waits until all services pass their
   healthchecks before returning.
3. Smoke-test a service:
   ```sh
   curl http://fhir-server-1:9111/ch-vacd-api-reference-server/fhir/metadata
   ```
   Should return a `CapabilityStatement`. The dev container shares the
   `vacd-net` bridge with the compose services, so service-name URLs
   resolve directly on macOS/Windows/Linux.

## Common commands

A small `Makefile` wraps the most-used compose calls. Run `make` (or
`make help`) for the list:

| Target         | What it does                                          |
| ---            | ---                                                   |
| `make restart` | `docker compose down && build && up --wait`           |
| `make log`     | `docker compose logs -f` — follow logs of all services |
| `make help`    | print the target list                                 |

Plain `docker compose ...` of course still works — the targets are just
shortcuts.

## Profiles

### DB

Take all db's up

docker compose --profile db up --wait

### BE

Take all be's and db's up

docker compose --profile be up --wait

### FE

Take all fe's, be's and db's up
docker compose --profile fe up --wait

## What you get
NAMES                      PORTS                                                   
vacd-login-frontend        0.0.0.0:3003->80/tcp, [::]:3003->80/tcp
vacd-openfhir              8080/tcp, 0.0.0.0:8083->8083/tcp, [::]:8083->8083/tcp
vacd-fhir-server-1         0.0.0.0:9111->9111/tcp, [::]:9111->9111/tcp
vacd-consumer-frontend     0.0.0.0:3004->80/tcp, [::]:3004->80/tcp
vacd-ehrbase               0.0.0.0:8082->8080/tcp, [::]:8082->8080/tcp
vacd-producer-frontend     0.0.0.0:3002->80/tcp, [::]:3002->80/tcp
vacd-mongo-db              27017/tcp
vacd-fhir-db               0.0.0.0:5433->5432/tcp, [::]:5433->5432/tcp
vacd-ehrbase-db            0.0.0.0:5434->5432/tcp, [::]:5434->5432/tcp
vacd-iam-mock              0.0.0.0:9090->8080/tcp, [::]:9090->8080/tcp
vacd-bff-producer-server   0.0.0.0:9112->9112/tcp, [::]:9112->9112/tcp
vacd-bff-consumer-server   0.0.0.0:8001->8001/tcp, [::]:8001->8001/tcp


Connection URLs are exported to the dev container as `FHIR_SERVER_1_URL`,
`FHIR_SERVER_2_URL`, `CDR_URL`, `MAPPER_URL` (see
`.devcontainer/devcontainer.json`). The two FHIR servers are
intentionally generic — the harness does **not** assign them
producer/consumer roles; teams decide their own topology.

## What you build

The challenge has three pieces, all participant-built:

- A **producer** that emits a **CH VACD Immunization Administration Document**.
- The **platform layer** that lands it in openEHR and serves it back out.
- A **consumer** that fetches and renders a **CH VACD Vaccination Record Document**.

The harness reserves host ports **3000–3001, 8000–8001, 8080–8081,
8888–8889** for your platform layer — all forwarded by the dev
container. Run your service inside the container terminal; reach it
from your host browser like any localhost service.

A **Platform Business Logic Example** lives at
[`services/platform-business-logic-example/`](services/platform-business-logic-example/)
— a minimal Kotlin/Ktor smoke-test that was used to validate the harness
end-to-end. It is **not** a reference architecture or starting point;
teams should design their own platform layer from scratch. To see it
run alongside the backing services:
```sh
docker compose --profile example up --wait
# then open http://localhost:8888/demo
```
Canonical test Bundles live under [`examples/`](examples/).

## Repo layout

```
.devcontainer/                      dev container config + post-create.sh
docker-compose.yml                  the backing services above
Makefile                            handy targets: `make restart`, `make log`
services/
  ch-vacd-api-reference-server/     HAPI FHIR + CH VACD ResourceProviders (from https://github.com/ralych)
  bff-producer/                     producer-side BFF (Java/Spring)
  bff-consumer/                     consumer-side BFF (FastAPI) — patient dossier + vaccinations
  iam-mock/                         minimal IAM stub issuing JWTs for the frontends
  platform-business-logic-example/  harness smoke-test (Kotlin/Ktor, not a template)
frontends/
  login/                            login frontend
  frontend-producer/                doctor (Arzt) frontend
  frontend-consumer/                patient frontend
examples/                           canonical CH VACD example Bundles + round-trip script
docs/
  api/                              OpenAPI specs for the BFF endpoints
  architecture/                     architecture diagrams + notes
  challenge/                        original hackathon brief + openEHR template
  demo/                             rendered PDF and screenshots of the example platform
  backend_for_frontend_consumer.md  consumer BFF design notes
progress/                           branch-scoped chronological progress notes
```

