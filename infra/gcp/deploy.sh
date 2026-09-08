#!/usr/bin/env bash

set -euo pipefail

PROJECT_ID="${PROJECT_ID:-bizlama}"
REGION="${REGION:-asia-south1}"
SERVICE="${SERVICE:-bizlama}"

PROJECT_NUMBER="$(gcloud projects describe "${PROJECT_ID}" --format='value(projectNumber)')"

IMAGE="${REGION}-docker.pkg.dev/${PROJECT_ID}/bizlama/app"

INSTANCE_CONNECTION_NAME="$(
  gcloud sql instances describe bizlama-db \
    --project="${PROJECT_ID}" \
    --format='value(connectionName)'
)"

RUNTIME_SERVICE_ACCOUNT="bizlama-runtime@${PROJECT_ID}.iam.gserviceaccount.com"

latest_enabled_secret_version() {
  gcloud secrets versions list "$1" \
    --project="${PROJECT_ID}" \
    --filter="state=ENABLED" \
    --sort-by="createTime" \
    --limit=1 \
    --format="value(name)"
}

DB_SECRET_VERSION="$(latest_enabled_secret_version bizlama-db-password)"
OWNER_SECRET_VERSION="$(latest_enabled_secret_version bizlama-owner-password)"
TOKEN_SECRET_VERSION="$(latest_enabled_secret_version bizlama-auth-token-secret)"

AUTH_MODE="${BIZLAMA_AUTH_MODE:-local}"

AUTH_ENV="BIZLAMA_AUTH_MODE=${AUTH_MODE}"

if [ "${AUTH_MODE}" = "identity-platform" ]; then
  if [ -z "${BIZLAMA_IDENTITY_API_KEY:-}" ]; then
    echo "BIZLAMA_IDENTITY_API_KEY is required when BIZLAMA_AUTH_MODE=identity-platform" >&2
    exit 1
  fi

  AUTH_ENV="${AUTH_ENV},BIZLAMA_IDENTITY_API_KEY=${BIZLAMA_IDENTITY_API_KEY}"
fi

gcloud builds submit \
  --project="${PROJECT_ID}" \
  --tag="${IMAGE}" .

gcloud run deploy "${SERVICE}" \
  --project="${PROJECT_ID}" \
  --region="${REGION}" \
  --image="${IMAGE}" \
  --service-account="${RUNTIME_SERVICE_ACCOUNT}" \
  --allow-unauthenticated \
  --min=0 \
  --max=3 \
  --memory=1Gi \
  --cpu=1 \
  --concurrency=40 \
  --set-secrets="BIZLAMA_DB_PASSWORD=bizlama-db-password:${DB_SECRET_VERSION},BIZLAMA_OWNER_PASSWORD=bizlama-owner-password:${OWNER_SECRET_VERSION},BIZLAMA_AUTH_TOKEN_SECRET=bizlama-auth-token-secret:${TOKEN_SECRET_VERSION}" \
  --set-env-vars="SPRING_PROFILES_ACTIVE=cloud,${AUTH_ENV},BIZLAMA_OWNER_EMAIL=${BIZLAMA_OWNER_EMAIL:-owner@bizlama.app},BIZLAMA_OWNER_NAME=${BIZLAMA_OWNER_NAME:-Madhu},BIZLAMA_GCP_PROJECT_ID=${PROJECT_ID},BIZLAMA_CLOUD_SQL_INSTANCE=${INSTANCE_CONNECTION_NAME},BIZLAMA_DB_NAME=bizlama,BIZLAMA_DB_USER=bizlama_app,BIZLAMA_RECEIPT_STORAGE=gcs,BIZLAMA_RECEIPTS_BUCKET=bizlama-receipts-${PROJECT_NUMBER},BIZLAMA_VERTEX_AI_ENABLED=true,BIZLAMA_BIGQUERY_ENABLED=true,BIZLAMA_BIGQUERY_DATASET=bizlama_analytics,GOOGLE_CLOUD_PROJECT=${PROJECT_ID},GOOGLE_CLOUD_LOCATION=global,GOOGLE_GENAI_USE_VERTEXAI=true"

SERVICE_URL="$(
  gcloud run services describe "${SERVICE}" \
    --project="${PROJECT_ID}" \
    --region="${REGION}" \
    --format='value(status.url)'
)"

echo "Deployed BizLaMa: ${SERVICE_URL}"
echo "Health check: ${SERVICE_URL}/actuator/health"