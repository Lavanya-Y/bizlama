# BizLaMa

BizLaMa is an AI-assisted kitchen operations workspace for small food businesses. It connects orders, versioned recipes, stock lots, expiry guidance, production, waste, customer feedback, and receipt evidence in one auditable workflow.

## What is implemented

- Persistent operational storage with version-controlled Flyway migrations.
- Local disk-backed H2 by default, PostgreSQL/Cloud SQL in the `cloud` profile, and an explicit in-memory fallback profile.
- Branded-product normalization for Indian-market examples such as Amul Paneer and Heritage High Protein Paneer.
- Natural-language and browser voice capture for purchases, production, and waste.
- Multi-item natural-language capture, including shorthand such as `2 kg salt` and `12 eggs`, with one atomic review.
- Confidence policy: routine actions above 90% can apply automatically; lower-confidence actions need review; recipe changes always need explicit owner approval.
- Receipt upload, private local/GCS object storage, Vertex AI extraction, editable human review, and stock confirmation.
- Orders, stock lots and movements, feedback, recipe versions, experiments, and activity audit history persisted in the database.
- BigQuery operational event publishing for analytics, with failures isolated from operational transactions.
- One Cloud Run container serving both the Angular UI and Spring Boot API.
- Stateless owner authentication with protected APIs, short-lived bearer tokens, Secret Manager-backed cloud credentials, and an optional Google Identity Platform mode.
- Searchable recipe versions plus bounded editors for any number of ingredients and preparation steps.
- Accessible contextual tooltips on navigation, automation decisions, and important controls.
- Server-side search, status filtering, aggregate summaries, detail-on-demand and pagination for large inventories and order histories.
- An owner-only settings area keeps infrastructure status away from daily operational pages.
- Extensible kitchen, location, user, supplier, category, order-history and analytics-outbox schema with workload-specific indexes.

## Run locally

Requirements: Java 21, Maven, Node.js 20+.

### Terminal 1

```bash
cd backend
JAVA_HOME=/path/to/jdk-21 mvn spring-boot:run
```

### Terminal 2

```bash
cd frontend
npm ci
npm start -- --host 127.0.0.1
```

Open `http://127.0.0.1:4200` and sign in with:

- Email: `owner@bizlama.local`
- Password: `bizlama-demo`

Those credentials are development-only and can be overridden with the variables in `.env.example`.

Data is stored in `backend/.data/bizlama.mv.db` and survives restarts.

To deliberately test the fallback instead, start the backend with:

```bash
--spring.profiles.active=memory
```

## Deploy to the prepared GCP project

In Cloud Shell, upload or clone this directory, then run from the repository root:

```bash
bash infra/gcp/bootstrap.sh
bash infra/gcp/deploy.sh
```

The scripts are idempotent. They reuse `bizlama-db` and the existing `bizlama-db-password`, create missing data/receipt buckets, create all BigQuery analytics tables, and prepare the runtime identity.

Bootstrap creates separate Secret Manager values for the owner password and token-signing key; it prints a generated owner password only on first creation.

Flyway creates or upgrades the complete Cloud SQL schema when the new revision starts.

No service-account key file is needed. Cloud Run uses its attached runtime identity and Application Default Credentials for Cloud SQL, Storage, BigQuery, Vertex AI, and Secret Manager.

## Production guardrails

- Never store database passwords or service-account JSON in source.
- Never deploy with the local demo password or development token secret. The GCP scripts inject generated values from Secret Manager.
- Keep receipt buckets private with public-access prevention.
- Printed expiry dates and owner overrides take precedence over shelf-life suggestions.
- Recipe proposals do not become active until the owner uses the activation action.
- The included single-owner authentication is appropriate for the current project and judging deployment.
- Tenant, user, location and role schema is already present for later onboarding: enable Identity Platform and enforce per-kitchen authorization before serving multiple independent businesses.

See `ARCHITECTURE.md` for data ownership, schema groups and large-list UX decisions.

## Optional Google Identity Platform mode

The backend can validate Identity Platform ID tokens and the Angular login can use email/password accounts directly.

Configure an Identity Platform email/password provider and web API key, then deploy with:

```bash
BIZLAMA_AUTH_MODE=identity-platform
BIZLAMA_GCP_PROJECT_ID=<your-project-id>
BIZLAMA_IDENTITY_API_KEY=<your-api-key>
```

The local mode remains the fastest secure path for the single-owner competition demo.