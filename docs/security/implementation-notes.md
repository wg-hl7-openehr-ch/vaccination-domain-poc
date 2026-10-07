# Security Implementation Notes — Vaccination Domain POC

> **Status:** Draft, verified against Keycloak 26.8 and HAPI FHIR 8.8.1
> (2026-10-05). Re-check after version upgrades.
> **Implements:** [security concept](security.md). Bare § references
> point to the concept; references within this document are written as
> "notes §x".

The concept says *what* must hold. This document records product
decisions and *how* the rules map onto Keycloak, HAPI FHIR and the BFF
frameworks — including limitations that are easy to miss.

---

## 1. Decision: Keycloak as Authorization Server

| Criterion | Extend `iam-mock` (Flask) | **Keycloak** |
| --- | --- | --- |
| OIDC / OAuth 2.0 compliance | Must be written and maintained by us | Certified OpenID provider |
| `private_key_jwt` client auth | Implement (assertion validation, `jti` replay cache, JWKS fetching) | Built in ("Signed JWT", JWKS URL per client, `jti` replay check) |
| External identity providers later (OIDC / SAML) | Implement | Identity brokering by configuration |
| MFA, brute-force protection, key rotation | Implement | Built in |
| Sender-constrained tokens, PAR (FAPI 2.0) | Implement | DPoP fully supported since 26.4; PAR built in |
| SMART specifics | Easy to hard-code | Scopes and claims via configuration and protocol mappers. `/.well-known/smart-configuration` and RAR for `client_credentials` are **not** provided (notes §2.7, notes §2.8). |
| Footprint | Tiny | ~0.5–1 GB RAM, own Postgres DB |

**Decision: Keycloak.** Standard endpoints, `private_key_jwt`, key
rotation, MFA and identity brokering come out of the box. Human flows
(MVP, proxy access) need **no custom Keycloak code**; only the M2M
patient binding does (notes §2.8). Pin the Keycloak version; several features
below appeared in 26.2–26.4.

---

## 2. Keycloak configuration

### 2.1 Realm and versioning

- Realm `vacd`, exported as `realm-vacd.json` and versioned (clients,
  client scopes, flows, User Profile, test users in the dev profile).
- **Pin user ids** in the export: `RelatedPerson.identifier` stores the
  account `sub` (§6.6), so ids must survive a re-import.
- The dev realm seeds the iam-mock users
  (`services/iam-mock/app/data/users.json`) 1:1, so existing test data
  stays valid: `patient1`…`patient4` get role `patient`, `patient` =
  their `user_id` (`00000000-…-00000000000{1..4}`, already used as FHIR
  `Patient` id) and `fhir_user_patient` = `Patient/<same id>`; `doctor1`
  gets role `practitioner` and `practitioner_role`,
  `fhir_user_practitioner` = `PractitionerRole/<same id>`, `gln`,
  `organization_reference`, `organization_gln` matching a seeded
  `PractitionerRole` / `Organization`.

### 2.2 Issuance policy: who may log in at which client (§5.3)

- Create a copy of the browser flow per BFF and bind it via
  *Clients → client → Advanced → Authentication flow overrides → Browser
  Flow* (`authenticationFlowBindingOverrides.browser`).
- Structure: a **REQUIRED** subflow holding the usual alternatives
  (cookie, identity providers, forms), followed by a top-level
  **CONDITIONAL** subflow with *Condition – user role* (`conditional-user-role`,
  role = `practitioner` resp. `patient`/`representative`, **Negate
  output = on**) and *Deny access* (`deny-access-authenticator`,
  REQUIRED).
- Do **not** put the role check inside the ALTERNATIVE forms subflow: an
  existing SSO session satisfies the cookie alternative and skips the
  check. Test with an existing session (AC6).
- Only browser and direct-grant flows can be overridden. Disable the
  direct grant (password grant) on both BFF clients.
- `bff-consumer` accepts two roles: put two *Condition – user role*
  executions (`patient`, `representative`), both with Negate output on,
  into the same conditional subflow. Conditions are ANDed, so access is
  denied only if the user has neither role. (A composite role `consumer`
  containing both does **not** work: composites pass their children to
  the holder, not the other way round.)

### 2.3 Client-specific claims and scopes

- Put protocol mappers on the client's dedicated scope
  (`<clientId>-dedicated`): they apply only to tokens for that client.
  This is how organization claims stay out of consumer tokens and
  `patient` stays out of producer tokens (§5.3).
  - `bff-consumer`: User Attribute mappers `patient` → `patient`,
    `fhir_user_patient` → `fhirUser`; Hardcoded claim
    `extensions.ch_vacd.caller_type` = `consumer`.
  - `bff-producer`: User Attribute mappers `fhir_user_practitioner` →
    `fhirUser`, `gln` → `extensions.ch_vacd.gln`, `organization_reference`
    → `extensions.ch_vacd.organization_reference`, `organization_gln` →
    `extensions.ch_vacd.organization_gln`; Hardcoded claim
    `extensions.ch_vacd.caller_type` = `practitioner`.
  - M2M clients: Hardcoded claims from onboarding
    (`extensions.ch_vacd.organization_reference`,
    `extensions.ch_vacd.organization_gln`,
    `extensions.ch_vacd.caller_type` = `system`).
  - Service clients (`fhir-api`, `gateway`): Hardcoded claim
    `extensions.ch_vacd.caller_type` = `service`.
  - Keycloak treats dots in a *Token Claim Name* as nesting, which
    produces the `extensions.ch_vacd` object. User Attribute mappers emit
    nothing when the attribute is absent — a representative without own
    `Patient` gets no `patient` / `fhirUser` claim.
- **SMART scopes** are client scopes whose names are the literal scope
  strings (`patient/Immunization.rs`, `__vacd_share_code_create`, …),
  linked as Default or Optional only to the clients allowed to have them.
  Keycloak has no SMART wildcard semantics — never create `patient/*.rs`.
- **Per-role scopes at `bff-consumer`** (§5.3): give each SMART client
  scope a *role scope mapping* (client scope → Scope tab). Keycloak
  applies a client scope with role scope mappings only to users holding
  one of those roles. Map `launch/patient`, `fhirUser` and the `patient/…`
  scopes to role `patient`, the consumer `user/…` scopes to `representative`,
  `__vacd_share_code_create` to both; link them all as Default on
  `bff-consumer`. Test with AC26.
- `fhirUser`: the User Attribute mapper copies values unchanged, so keep
  the full reference in separate managed attributes —
  `fhir_user_patient` (`Patient/{patient}`) and `fhir_user_practitioner`
  (`PractitionerRole/{practitioner_role}`) — maintained together with
  `patient` / `practitioner_role`. Two attributes are needed because one
  account may hold both roles (§5.1); each is mapped only on the dedicated
  scope of its client. The RS checks `fhirUser` against `patient` (§8.3).

### 2.4 Audience and token type (§8.2)

- Keycloak ignores the SMART `aud` authorize parameter. Add an
  **Audience** mapper with *Included Custom Audience* = FHIR base URL
  (e.g. `https://api.vacd.example.ch/fhir`) to every client that calls
  the API, and one with the audit logger URL to the service clients
  (§9.4) — and to nothing else.
- Enable *Use "at+jwt" as access token header type*
  (`access.token.header.type.rfc9068`, since 26.2) on all clients. The
  RS checks the **header** `typ`; Keycloak's payload claim `typ` (`Bearer`,
  `ID`, `Refresh`, …) is an additional check.

### 2.5 User Profile (§5.2)

- Declare `patient`, `fhir_user_patient`, `practitioner_role`,
  `fhir_user_practitioner`, `gln`, `organization_reference`,
  `organization_gln` as attributes with
  `"permissions": {"view": ["admin"], "edit": ["admin"]}`.
- Leave *Unmanaged Attributes* = **Disabled** (`unmanagedAttributePolicy`
  absent).

### 2.6 Client authentication (§7.3, §5.5)

- All confidential clients (BFFs, service clients, M2M): Client
  Authenticator **Signed JWT** (`private_key_jwt`), *Use JWKS URL* on
  (`use.jwks.url`, `jwks.url`) or a registered public key. Disable the
  direct access grant (password grant) on every client.
- *Signature algorithm* (`token.endpoint.auth.signing.alg`, since 26.2):
  `RS384` or `ES384` for all confidential clients (§7.3).
- *Max expiration* (`token.endpoint.auth.signing.max.exp`): default is
  60 s; SMART allows up to 300 s — set 300 for M2M clients.
- `jti` replay protection is built in (reuse fails with "Token reuse
  detected").
- JWKS URL fetching happens from Keycloak: restrict its egress (proxy or
  firewall allow-list of registered hosts) against SSRF.

### 2.7 SMART discovery

Keycloak does not serve `/.well-known/smart-configuration`. The FHIR API
serves it at its base URL, with the Keycloak authorization, token and
JWKS endpoints, `token_endpoint_auth_methods_supported:
["private_key_jwt"]`, `token_endpoint_auth_signing_alg_values_supported:
["RS384", "ES384"]`, the supported scopes and capabilities.

### 2.8 M2M patient binding (RAR) — needs a spike

- Keycloak's `AuthorizationDetailsProcessor` SPI
  (`org.keycloak.protocol.oidc.rar`) is **internal** (unsupported) and
  is **not invoked** for `grant_type=client_credentials` (only for
  authorization code, pre-authorized code and refresh).
- For `client_credentials`, Keycloak stores the raw `authorization_details`
  parameter as client-session note `authorization_details` "to support
  custom protocol mappers using RAR".
- Proposed approach: a **custom protocol mapper** on M2M clients that
  reads that note, accepts only `type = ch-vacd-context` with exactly one
  `identifier` of the form `Patient/{id}`, and writes `fhirContext` and
  `authorization_details` into the token. Malformed input → no
  `fhirContext` (the token is then usable only for `$redeem-share-code`,
  `$withdraw` and terminology calls, §6.7, §8.6).
- Risks: relies on internal behaviour. Pin the version, cover it with
  integration tests, re-verify on every upgrade. Fallback: a custom grant
  type (`oauth2-grant-type` SPI) or a small token-exchange service in
  front of Keycloak.

### 2.9 Sessions, logout, administration

- BFF clients: *Front channel logout* off, *Backchannel logout URL* set,
  *Backchannel logout session required* on; RP-initiated logout via the
  end-session endpoint with `id_token_hint`; explicit *Valid post logout
  redirect URIs*.
- Disable impersonation: `KC_FEATURE_IMPERSONATION=disabled` (feature
  `impersonation`).
- Realm settings → Events: save **login events** and **admin events**
  with *Include representation*.
- Brute-force detection and password policy on; OTP / WebAuthn enforced
  for practitioners, administrators and auditors (conditional OTP step
  bound to these roles, or required action per account).
- Admin console reachable only from the operator network (separate
  hostname / `KC_HOSTNAME_ADMIN`), not through the edge proxy.

### 2.10 Later hardening

DPoP (`dpop:v1`, fully supported since 26.4; per client *Require DPoP
bound tokens*) and PAR (*Pushed authorization request required*). FAPI 2
client profiles exist (`fapi-2-dpop-security-profile`). With a gateway in
front of the FHIR API, decide which component validates the DPoP proof:
`htu` is the URL the client called (the gateway).

---

## 3. HAPI FHIR enforcement (plain server)

The CH VACD FHIR API is a **plain `RestfulServer`** with custom
`IResourceProvider`s (HAPI 8.8.1, no JPA, no `hapi-fhir-storage`). This
matters, because many HAPI security features hook into storage events
that only the JPA server fires.

### 3.1 Which hooks fire

| Pointcut | Fired on plain server? |
| --- | --- |
| `SERVER_INCOMING_REQUEST_PRE_HANDLED` (request incl. parsed body) | ✓ for every read, search, create, update, operation |
| `SERVER_OUTGOING_RESPONSE` | ✓ read, search, operations; create/update when the response contains the resource |
| `SERVER_PROCESSING_COMPLETED_NORMALLY`, `SERVER_HANDLE_EXCEPTION` | ✓ |
| `STORAGE_PRESHOW_RESOURCES`, `STORAGE_PREACCESS_RESOURCES` | ✗ |
| `STORAGE_PRESTORAGE_RESOURCE_*`, `STORAGE_PRECOMMIT_RESOURCE_*` | ✗ |

The storage pointcuts fire only if provider code calls
`requestDetails.getInterceptorBroadcaster().callHooks(...)` itself.

### 3.2 `AuthorizationInterceptor`

- Denies by default; `/metadata` needs `allow().metadata()`.
- **Read / search:** checked on the response (`SERVER_OUTGOING_RESPONSE`);
  works on a plain server. Compartment search rules block searches
  without a matching `patient` parameter up front.
- **Create:** the incoming body is checked. A document `Bundle` is never
  in the Patient compartment → allow `create` of `Bundle` and check the
  entries in a custom rule tester or in the provider (§8.5, §8.7).
- **Update:** only the **new body** is checked; the stored resource is
  seen only via `STORAGE_PRESTORAGE_RESOURCE_UPDATED`, which does not
  fire. This is one reason the concept replaces `PUT /Immunization` with
  `$mark-entered-in-error` (§6.5): no `update` rule is needed at all. The
  operation provider loads the stored resource and checks patient and
  recording organization itself (§8.7).
- **Operations** (`$export-document`, `$create-share-code`,
  `$redeem-share-code`, `$withdraw`, `$mark-entered-in-error`,
  `$represented-persons`, `$expand`)
  need explicit
  `allow().operation().named(...)` rules. Without `andAllowAllResponses()`
  the output document is unpacked one level and every entry is checked
  as a read: `Practitioner`, `PractitionerRole`, `Organization`,
  `Medication` fall to the default deny. Either allow all responses and
  check the instance id on the way in plus a custom response check
  (§8.8), or add explicit read rules for these types.
- The existing `@Validate` (`$validate`) on `BundleProvider` stays
  denied: it has no row in §8.4.
- R4 Patient compartment (verified on 8.8.1): `Practitioner`,
  `PractitionerRole`, `Organization`, `Bundle`, `Medication`, `Location`,
  `Binary` are **not** members.

### 3.3 `ConsentInterceptor`

`startOperation` (request) and `willSeeResource` (every resource of the
response, incl. document entries) work. `canSeeResource` is called only
from `STORAGE_PREACCESS_RESOURCES` and is **never** invoked on this
server. Put consent and response filtering into `startOperation` /
`willSeeResource`.

### 3.4 Audit

`BalpAuditCaptureInterceptor` lives in `hapi-fhir-storage` (heavy JPA and
Hibernate Search dependencies) and hooks only storage pointcuts — on this
server it would record nothing, and it never audits operations. Write a
small custom audit interceptor on `SERVER_INCOMING_REQUEST_PRE_HANDLED`
(intent for writes), `SERVER_OUTGOING_RESPONSE` (reads, before release)
and `SERVER_PROCESSING_COMPLETED` / `SERVER_HANDLE_EXCEPTION` (outcome),
following the BALP field mapping in §9.3. Do not base it on
`ChVacdLoggingInterceptor`, which is a payload logger.

### 3.5 Side effects outside the hooks

`BundleBusinessServiceImpl.createBundle` creates patients, directory
resources and EHRbase compositions without firing any hook. All write
checks (§8.7) must run **before** these side effects — in the provider or
business layer, not only in interceptors.

### 3.6 Wiring

- Interceptors are registered in `FhirServletConfig.fhirServlet`. Register
  token validation and authorization **before** the validating and
  logging interceptors (or set `@Interceptor(order = …)`).
- Token validation: Spring Security resource server in front of the
  servlet, or a custom interceptor; put the validated claims into the
  `RequestDetails` user data for the later steps.
- Only the providers listed in `fhir.providers` are loaded; an empty list
  loads **all** providers. Keep the list explicit and minimal.

---

## 4. BFFs (§8.10)

### 4.1 bff-producer (Spring Boot)

- Spring Security `oauth2Login()` with `OAuth2AuthorizedClientManager`;
  `private_key_jwt` via `NimbusJwtClientAuthenticationParametersConverter`.
- Session fixation protection (default `migrateSession`) stays on; Spring
  Security CSRF protection on for all state-changing endpoints.
- Replaces `JwtAuthFilter` and the `permitAll()` rule.

### 4.2 bff-consumer (FastAPI)

- OAuth client: Authlib (authorization code + PKCE, `private_key_jwt`).
- **Server-side session store** (e.g. Redis or a database table) with
  only a random session id in the cookie. Starlette's `SessionMiddleware`
  stores the session *in the cookie* (signed, not encrypted) — it must not
  hold tokens.
- CSRF: double-submit token or synchronizer token plus `Origin` check.

---

## 5. Audit logger

- Own database (not on the FHIR API's Postgres instance) and two roles:
  a schema owner for migrations and an application role with `INSERT` and
  `SELECT` only.
- Replace `ddl-auto: update` with versioned migrations, so the
  application role needs no DDL rights.
- HTTP read and search on `AuditEvent` are disabled in the secured
  deployment (§9.4); auditors read through a read-only database role.
  If an HTTP read path is added later, implement the advertised search
  parameters instead of `findAll()`.
