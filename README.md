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
| `adapters/sms` | The bank's SMS vendors (SMS Xabar first) as `sms-adapter-api`; callers use `sms-adapter-client` | `./gradlew -p adapters/sms build` |
| `adapters/taf` | The TAF agent's API as `taf-adapter-api` (registration and login: code, verdicts, face confirmation, phone owner, health); callers use `taf-adapter-client` | `./gradlew -p adapters/taf build` |

Images: `Dockerfile` builds one deployable (`--build-arg SERVICE=adapters/myid`); `.github/workflows/images.yml`
publishes `ghcr.io/khakimjanovich/myid-adapter`, `ghcr.io/khakimjanovich/sms-adapter` and `ghcr.io/khakimjanovich/taf-adapter` (`main`, `sha-<commit>`, and `X.Y.Z` for a tag `vX.Y.Z`), which
[heeyid-infra](../heeyid-infra) pulls. `.github/workflows/build.yml` runs each adapter's checks.

`build.yml` and `images.yml` run on the owner's self-hosted runner `mac-heeyid` (`~/actions-runners/heeyid`,
secret `PACKAGES_READ_TOKEN`: a classic token with `read:packages`). Images build on one BuildKit builder,
`heeyid-builder`, created once on the Mac (if it is ever gone, the next job creates it again):

```bash
DOCKER_CONFIG=~/actions-runners/heeyid/.docker docker buildx create --name heeyid-builder --driver docker-container --bootstrap
```

To build against local checkouts instead of the published releases:
`./gradlew -p adapters/myid build --include-build ../acquiring/contracts --include-build ../acquiring/platform`.

### MyID adapter settings

No value has a default: a missing one stops startup.

| Variable | What |
|---|---|
| `MYID_BASE_URL`, `MYID_MEDIA_URL` | MyID's host and its media host (Inplace photos) |
| `MYID_CONNECT_TIMEOUT`, `MYID_READ_TIMEOUT` | e.g. `2s`, `7s`: MyID answers most calls in 5 s |
| `MYID_LONG_READ_TIMEOUT` | e.g. `35s`: the wait for `/users/me`, the Inplace job result and the two compare results, which MyID answers in up to 30 s |
| `MYID_BREAKER_FAILURES`, `MYID_BREAKER_PROBE_AFTER` | the MyID breaker (SAD §8: `5`, `30s`) |
| `VALKEY_HOST`, `VALKEY_PORT`, `VALKEY_USERNAME`, `VALKEY_PASSWORD` | Valkey and the adapter's ACL user |
| `MYID_ADAPTER_TOKEN_KEY` | AES-256 key, 32 bytes in base64: the MyID tokens in Valkey are encrypted with it (SAD TB-5) |
| `MYID_TRUSTED_CAS` | MyID's CA (PEM, or `file:` path): MyID is trusted only through it (SAD TB-5, pinned CA) |
| `MYID_CLIENT_ID`, `MYID_CLIENT_SECRET`, `MYID_USERNAME`, `MYID_PASSWORD` | the adapter's MyID credential, one per installation (SAD v3.2 C-1): the client, and the login of the Backend API without personal data (the password grant); all required |
| `MYID_ADAPTER_PORT`, `MYID_ADAPTER_MANAGEMENT_PORT` | the API port (mutual TLS) and the health port |
| `MYID_ADAPTER_SSL_CERTIFICATE`, `MYID_ADAPTER_SSL_PRIVATE_KEY`, `MYID_ADAPTER_SSL_TRUSTED_CAS` | the adapter's certificate and the CAs of its callers |
| `MYID_ADAPTER_ISSUER_URI`, `MYID_ADAPTER_AUDIENCE` | heeyID's Keycloak and the adapter's audience; callers need the realm role `myid.use` |
| `TRACING_SAMPLING`, `OTLP_*_ENDPOINT` | the platform's observability |

The adapter's MyID tokens are in Valkey (keys `myid-adapter:token:<grant>`), encrypted with
AES-256-GCM, shared by all instances and kept until 5 minutes before MyID's expiry; a token written with an
older key is replaced; a token MyID refuses (403) is renewed once.
After 5 failures in a row (no answer in time, or a 5xx) the breaker opens: calls are
`MYID_ADAPTER_SERVICE_UNAVAILABLE` without a call to MyID, one probe each 30 s, and the gauge
`myid.breaker.open` is 1. No request or response body reaches a log.

### SMS adapter

One port for the bank's SMS vendors. The caller gives the final text and an idempotency key; the
adapter sends through `SMS_ACTIVE_VENDOR`, then through `SMS_FAILOVER_VENDORS` in order when a vendor
is down. Message rules (BRD FR-75, FR-75a; SRS-NTF-01…04): `OTP` in the side apps always and in the
master app only while TAF is down; `SECURITY_NOTICE` for a new session, a new phone, a password
reset, a number on hold or a block (the gateway sends none to a number on hold). Push never carries a
code; that rule is the gateway's.

| Vendor answer | Result | Next vendor |
|---|---|---|
| 2xx | `ACCEPTED` | no |
| request sent, no answer in time | `UNKNOWN` (the vendor may send it) | no: a code never goes out twice |
| no connection, 5xx, 429, wrong login, wrong URL, breaker open, Xabar error 100 or 102 | tried next; all down: `SMS_ADAPTER_SERVICE_UNAVAILABLE` (503), key free | yes |
| Xabar 400 with any other error code | `SMS_ADAPTER_MESSAGE_REFUSED` (502), key free | no |

The same key and body answer the first result and send nothing; the same key with another body is
`SMS_ADAPTER_KEY_CONFLICT`. Keys and statuses are in Valkey (`sms-adapter:key:*`,
`sms-adapter:message:*`, ACL `+get +set +del`), kept for `SMS_RETENTION`; Valkey keeps no text and no
phone number. No body reaches a log. Each vendor has a breaker (gauge
`sms.vendor.breaker.open{vendor}`).

Delivery reports: Xabar pushes statuses to the partner URL given to Xabar,
`https://<ingress>/delivery-reports/xabar/<XABAR_CALLBACK_SECRET>` (Xabar adds `/status`). They come
on `SMS_ADAPTER_CALLBACK_PORT`: plain HTTP, no token, reached only through the bank's ingress, which
ends TLS and lets in only Xabar's addresses. That port serves only `/delivery-reports/**`, and the API
port refuses those paths. A wrong secret is 404. Status words: `delivered`, `expired`, failures
(`rejected`, `undelivered`, …), anything else `REPORTED` with the raw word in `vendorStatus`. Xabar's
document lists no words and no callback auth: ask Xabar for both.

No value has a default: a missing one stops startup. A vendor's values are needed only when it is
named in `SMS_ACTIVE_VENDOR` or `SMS_FAILOVER_VENDORS`.

| Variable | What |
|---|---|
| `SMS_ACTIVE_VENDOR` | the vendor that sends first: `xabar` |
| `SMS_FAILOVER_VENDORS` | the vendors tried next, comma separated, in order; empty for none |
| `SMS_MESSAGE_ID_PREFIX` | 1 to 8 of `a-z0-9`, e.g. `hid`: the start of every message id (Xabar sees it; ids are 20 characters) |
| `SMS_RETENTION` | e.g. `7d`: how long a status and its key are kept |
| `SMS_BREAKER_FAILURES`, `SMS_BREAKER_PROBE_AFTER` | each vendor's breaker (SAD §8: `5`, `30s`) |
| `SMS_ADAPTER_CALLBACK_PORT` | the port of the vendors' delivery reports |
| `XABAR_BASE_URL` | `https://send.smsxabar.uz/broker-api` (the adapter calls `/send`) |
| `XABAR_USERNAME`, `XABAR_PASSWORD` | the adapter's Basic login at Xabar |
| `XABAR_ORIGINATOR` | the sender name registered at Xabar |
| `XABAR_CONNECT_TIMEOUT`, `XABAR_READ_TIMEOUT` | e.g. `2s`, `10s` |
| `XABAR_CALLBACK_SECRET` | 32 to 128 URL-safe characters, the secret in the partner URL |
| `VALKEY_HOST`, `VALKEY_PORT`, `VALKEY_USERNAME`, `VALKEY_PASSWORD` | Valkey and the adapter's ACL user (`~sms-adapter:*`) |
| `SMS_ADAPTER_PORT`, `SMS_ADAPTER_MANAGEMENT_PORT` | the API port (mutual TLS) and the health port |
| `SMS_ADAPTER_SSL_CERTIFICATE`, `SMS_ADAPTER_SSL_PRIVATE_KEY`, `SMS_ADAPTER_SSL_TRUSTED_CAS` | the adapter's certificate and the CAs of its callers |
| `SMS_ADAPTER_ISSUER_URI`, `SMS_ADAPTER_AUDIENCE` | heeyID's Keycloak and the adapter's audience; callers need the realm role `sms.use` |
| `TRACING_SAMPLING`, `OTLP_*_ENDPOINT` | the platform's observability |

#### Adding a second vendor

A vendor is one package, `adapters/sms/src/main/java/uz/heeyid/smsadapter/vendor/<name>/`, laid out as
`vendor/xabar` (the platform's package rules hold inside it):

1. `client/`: an `@HttpExchange` interface for the vendor's send call, and a class that implements
   `SmsVendor`: `name()` is the name used in `SMS_ACTIVE_VENDOR` / `SMS_FAILOVER_VENDORS`,
   `longestCall()` is its connect plus read timeout, and `send()` returns a `VendorOutcome` and never
   throws: `ACCEPTED`, `UNKNOWN` (sent, no answer in time), `REFUSED` (the message itself is wrong),
   `UNAVAILABLE` (nothing sent; the next vendor is tried).
2. `property/`: its `@ConfigurationProperties` record with no defaults, and its `${…}` values in
   `application.yaml`.
3. `config/`: a `@Configuration` with `@ConditionalOnVendor("<name>")` and
   `@EnableConfigurationProperties`, which builds the client and the `SmsVendor` bean. Nothing else
   registers it: `VendorConfig` finds every `SmsVendor` bean by name.
4. If the vendor reports deliveries: `model/` for its push body, a `service/` that checks its secret
   and calls `SmsService.report(...)`, and a `controller/` on `/delivery-reports/<name>/...` (both
   `@ConditionalOnVendor`). The callback port and the token-free route `POST /delivery-reports/**`
   already cover it.
5. Its constants in `utils/<Name>Constants`, a simulator in `SmsAdapterIT` (WireMock), and its
   variables in the table above. `SmsAdapterIT.Backup` shows the smallest vendor bean.

### TAF adapter settings

No value has a default: a missing one stops startup.

| Variable | What |
|---|---|
| `TAF_AUTH_URL` | TAF's OAuth 2.0 token endpoint, the full URL |
| `TAF_AGENT_URL` | the TAF agent in the network of the bank (its API host) |
| `TAF_HEALTH_URL` | the agent's health, the full URL (port 8081, `/actuator/health`) |
| `TAF_CONNECT_TIMEOUT`, `TAF_READ_TIMEOUT` | e.g. `2s`, `5s` (SAD C-15: 5 s for each TAF call) |
| `TAF_BREAKER_FAILURES`, `TAF_BREAKER_PROBE_AFTER` | the TAF breaker (SAD C-15: `5`, `30s`) |
| `TAF_CLIENT_ID`, `TAF_CLIENT_SECRET`, `TAF_SCOPE` | the adapter's TAF credential (client_credentials), one per installation |
| `TAF_TRUSTED_CAS` | the CA of the TAF hosts (PEM, or `file:` path): TAF is trusted only through it |
| `TAF_ADAPTER_TOKEN_KEY` | AES-256 key, 32 bytes in base64: the TAF token in Valkey is encrypted with it |
| `VALKEY_HOST`, `VALKEY_PORT`, `VALKEY_USERNAME`, `VALKEY_PASSWORD` | Valkey and the adapter's ACL user (keys `taf-adapter:*`) |
| `TAF_ADAPTER_PORT`, `TAF_ADAPTER_MANAGEMENT_PORT` | the API port (mutual TLS) and the health port |
| `TAF_ADAPTER_SSL_CERTIFICATE`, `TAF_ADAPTER_SSL_PRIVATE_KEY`, `TAF_ADAPTER_SSL_TRUSTED_CAS` | the adapter's certificate and the CAs of its callers |
| `TAF_ADAPTER_ISSUER_URI`, `TAF_ADAPTER_AUDIENCE` | heeyID's Keycloak and the adapter's audience; callers need the realm role `taf.use` |
| `TRACING_SAMPLING`, `OTLP_*_ENDPOINT` | the platform's observability |

The TAF token is in Valkey (key `taf-adapter:token`), encrypted with AES-256-GCM, shared by all instances
and kept until 5 minutes before TAF's expiry (3 h); a token TAF refuses (401) is renewed once. After 5
failures in a row (no answer in time, or a 5xx) the breaker opens: calls are `TAF_ADAPTER_SERVICE_UNAVAILABLE`
without a call to TAF, one probe each 30 s, and the gauge `taf.breaker.open` is 1. `GET /v1/health` is
`DOWN` then, so the gateway sends codes through the bank SMS service. TAF code 39 (`TAF_ADAPTER_TRUSTED_BANK_REFUSED`)
is not an outage: no other channel may send that code. No request or response body reaches a log.

Not in the adapter: the P2P operation calls (a later phase), and `POST /internal/taf/sms-status`, which the
TAF agent calls on the customer gateway (SAD §4.14 step 8). The customer texts of TAF's reason codes
(SRS Appendix C) are the gateway's: `mainReason` comes as TAF sends it.
