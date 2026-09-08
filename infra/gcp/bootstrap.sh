#!/usr/bin/env bash

set -euo pipefail

PROJECT_ID="${PROJECT_ID:-bizlama}"
REGION="${REGION:-asia-south1}"

RUNTIME_SERVICE_ACCOUNT="bizlama-runtime@${PROJECT_ID}.iam.gserviceaccount.com"
PROJECT_NUMBER="$(gcloud projects describe "${PROJECT_ID}" --format='value(projectNumber)')"

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

DATA_BUCKET="bizlama-data-${PROJECT_NUMBER}"
RECEIPTS_BUCKET="bizlama-receipts-${PROJECT_NUMBER}"

gcloud config set project "${PROJECT_ID}"

gcloud services enable \
  run.googleapis.com \
  artifactregistry.googleapis.com \
  cloudbuild.googleapis.com \
  sqladmin.googleapis.com \
  bigquery.googleapis.com \
  storage.googleapis.com \
  aiplatform.googleapis.com \
  secretmanager.googleapis.com \
  identitytoolkit.googleapis.com

gcloud sql instances describe bizlama-db \
  --project="${PROJECT_ID}" >/dev/null || {
  echo "Cloud SQL instance bizlama-db is missing. Create it before running bootstrap." >&2
  exit 1
}

gcloud secrets describe bizlama-db-password \
  --project="${PROJECT_ID}" >/dev/null || {
  echo "Secret bizlama-db-password is missing. Create it with the bizlama_app database password." >&2
  exit 1
}

gcloud storage buckets describe "gs://${DATA_BUCKET}" \
  --project="${PROJECT_ID}" >/dev/null 2>&1 || \
gcloud storage buckets create "gs://${DATA_BUCKET}" \
  --project="${PROJECT_ID}" \
  --location="${REGION}" \
  --default-storage-class=STANDARD \
  --uniform-bucket-level-access \
  --public-access-prevention

gcloud storage buckets describe "gs://${RECEIPTS_BUCKET}" \
  --project="${PROJECT_ID}" >/dev/null 2>&1 || \
gcloud storage buckets create "gs://${RECEIPTS_BUCKET}" \
  --project="${PROJECT_ID}" \
  --location="${REGION}" \
  --default-storage-class=STANDARD \
  --uniform-bucket-level-access \
  --public-access-prevention

AUTH_PASSWORD_CREATED="false"

if ! gcloud secrets describe bizlama-owner-password \
  --project="${PROJECT_ID}" >/dev/null 2>&1; then

  OWNER_PASSWORD="${BIZLAMA_OWNER_PASSWORD:-$(openssl rand -base64 18)}"

  printf '%s' "${OWNER_PASSWORD}" | \
    gcloud secrets create bizlama-owner-password \
      --project="${PROJECT_ID}" \
      --replication-policy=automatic \
      --data-file=-

  AUTH_PASSWORD_CREATED="true"
fi

if ! gcloud secrets describe bizlama-auth-token-secret \
  --project="${PROJECT_ID}" >/dev/null 2>&1; then

  TOKEN_SECRET="$(openssl rand -base64 48)"

  printf '%s' "${TOKEN_SECRET}" | \
    gcloud secrets create bizlama-auth-token-secret \
      --project="${PROJECT_ID}" \
      --replication-policy=automatic \
      --data-file=-
fi

gcloud artifacts repositories describe bizlama \
  --location="${REGION}" >/dev/null 2>&1 || \
gcloud artifacts repositories create bizlama \
  --repository-format=docker \
  --location="${REGION}" \
  --description="BizLaMa application images"

gcloud iam service-accounts describe "${RUNTIME_SERVICE_ACCOUNT}" \
  >/dev/null 2>&1 || \
gcloud iam service-accounts create bizlama-runtime \
  --display-name="BizLaMa Cloud Run runtime"

for role in \
  roles/cloudsql.client \
  roles/aiplatform.user \
  roles/bigquery.dataEditor \
  roles/bigquery.jobUser \
  roles/secretmanager.secretAccessor
do
  gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
    --member="serviceAccount:${RUNTIME_SERVICE_ACCOUNT}" \
    --role="${role}" \
    --condition=None >/dev/null
done

gcloud storage buckets add-iam-policy-binding "gs://${RECEIPTS_BUCKET}" \
  --member="serviceAccount:${RUNTIME_SERVICE_ACCOUNT}" \
  --role=roles/storage.objectUser >/dev/null

gcloud storage buckets add-iam-policy-binding "gs://${DATA_BUCKET}" \
  --member="serviceAccount:${RUNTIME_SERVICE_ACCOUNT}" \
  --role=roles/storage.objectViewer >/dev/null

bq --location="${REGION}" mk --dataset "${PROJECT_ID}:bizlama_analytics" 2>/dev/null || true

sed "s/PROJECT_ID_PLACEHOLDER/${PROJECT_ID}/g" \
  "${SCRIPT_DIR}/bigquery-schema.sql" | \
  bq query \
    --use_legacy_sql=false \
    --location="${REGION}"

echo "Bootstrap complete. Runtime service account: ${RUNTIME_SERVICE_ACCOUNT}"
echo "Receipt bucket: ${RECEIPTS_BUCKET}"
echo "BigQuery schema applied from: ${SCRIPT_DIR}/bigquery-schema.sql"

if [[ "${AUTH_PASSWORD_CREATED}" == "true" ]]; then
  echo "BizLaMa owner email: ${BIZLAMA_OWNER_EMAIL:-owner@bizlama.app}"
  echo "One-time generated owner password: ${OWNER_PASSWORD}"
  echo "Save this password now. It is stored in Secret Manager and will not be printed again."
fi