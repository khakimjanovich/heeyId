# heeyid

heeyID MVP 1: a customer identity platform that a bank installs in its own perimeter. One Gradle build (SAD ADR-25), layout from SAD §10.1: `gateway/` (domain, ports, adapters, customer gateway, admin gateway, workers), `keycloak-ext/`, `catalogue/`, `mobile/`, `panel/`, `api/`, `tests/`, `docs/`.

Deployment (SAD §10.1 `deploy/`) lives in [heeyid-infra](../heeyid-infra), not here.

Sources: BRD v1.5, SRS MVP 1, SAD MVP 1 v2.2. Lessons: `uz-video/SERIES-HEEYID.md`.

## Adapters

Each adapter is its own Gradle build and its own deployable (SAD v2.6, ADR-12). The platform release is
`platformVersion` in `gradle.properties`; the contracts release is `gradle/contracts.versions.toml`.
Reading both from GitHub Packages needs `GH_PACKAGES_ACTOR` and `GH_PACKAGES_TOKEN`.

| Deployable | What | Built by |
|---|---|---|
| `adapters/myid` | MyID's REST API as `myid-adapter-api` (every current MyID flow); callers use `myid-adapter-client` | `./gradlew -p adapters/myid build` |

Images: `Dockerfile` builds one deployable (`--build-arg SERVICE=adapters/myid`); `.github/workflows/images.yml`
publishes `ghcr.io/khakimjanovich/myid-adapter` (`main`, `sha-<commit>`, and `X.Y.Z` for a tag `vX.Y.Z`), which
[heeyid-infra](../heeyid-infra) pulls. `.github/workflows/build.yml` runs each adapter's checks.

To build against local checkouts instead of the published releases:
`./gradlew -p adapters/myid build --include-build ../acquiring/contracts --include-build ../acquiring/platform`.

### MyID adapter settings

No value has a default: a missing one stops startup.

| Variable | What |
|---|---|
| `MYID_BASE_URL`, `MYID_MEDIA_URL` | MyID's host and its media host (Inplace photos) |
| `MYID_CONNECT_TIMEOUT`, `MYID_READ_TIMEOUT` | e.g. `2s`, `35s` (MyID answers a result in up to 30 s) |
| `MYID_BREAKER_FAILURES`, `MYID_BREAKER_PROBE_AFTER` | the MyID breaker (SAD §8: `5`, `30s`) |
| `VALKEY_HOST`, `VALKEY_PORT`, `VALKEY_USERNAME`, `VALKEY_PASSWORD` | Valkey and the adapter's ACL user |
| `MYID_ADAPTER_TENANTS_FILE` | a YAML file the deployment renders from Vault: `myid.tenants.<tenant>.client-id`, `client-secret`, and `username`, `password` for the Backend API without personal data |
| `MYID_ADAPTER_PORT`, `MYID_ADAPTER_MANAGEMENT_PORT` | the API port (mutual TLS) and the health port |
| `MYID_ADAPTER_SSL_CERTIFICATE`, `MYID_ADAPTER_SSL_PRIVATE_KEY`, `MYID_ADAPTER_SSL_TRUSTED_CAS` | the adapter's certificate and the CAs of its callers |
| `MYID_ADAPTER_ISSUER_URI`, `MYID_ADAPTER_AUDIENCE` | heeyID's Keycloak and the adapter's audience; callers need the realm role `myid.use` |
| `TRACING_SAMPLING`, `OTLP_*_ENDPOINT` | the platform's observability |

Each tenant's MyID tokens are in Valkey (keys `<tenant>:myid-adapter:token:<grant>`), shared by all
instances and kept until 5 minutes before MyID's expiry; a token MyID refuses (403) is renewed once.
After 5 failures in a row (no answer in time, or a 5xx) the breaker opens: calls are
`MYID_ADAPTER_SERVICE_UNAVAILABLE` without a call to MyID, one probe each 30 s, and the gauge
`myid.breaker.open` is 1. No request or response body reaches a log.
