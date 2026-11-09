# heeyid

heeyID MVP 1: a customer identity platform that a bank installs in its own perimeter. One Gradle build (SAD ADR-25), layout from SAD §10.1: `gateway/` (domain, ports, adapters, customer gateway, admin gateway, workers), `keycloak-ext/`, `catalogue/`, `mobile/`, `panel/`, `api/`, `tests/`, `docs/`.

Deployment (SAD §10.1 `deploy/`) lives in [heeyid-infra](../heeyid-infra), not here.

Sources: BRD v1.5, SRS MVP 1, SAD MVP 1 v2.2. Lessons: `uz-video/SERIES-HEEYID.md`.
