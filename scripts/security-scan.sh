#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Both scanners run locally. Trivy downloads its public vulnerability database;
# --offline-scan disables dependency-resolution API requests and uploads no source.
command -v gitleaks >/dev/null
command -v trivy >/dev/null
gitleaks git --redact=100 --no-banner --config .gitleaks.toml
gitleaks dir --redact=100 --no-banner --config .gitleaks.toml .
trivy fs --ignorefile .trivyignore.yaml --offline-scan --scanners vuln --severity HIGH,CRITICAL --exit-code 1 \
  --skip-dirs frontend/build --skip-dirs frontend/.dart_tool \
  --skip-dirs frontend/android/.gradle --skip-dirs backend/target .
