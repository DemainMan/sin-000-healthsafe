#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODULES=(
  ingestion-service
  alert-level-service
  ward-service
  staffing-service
  equipment-alert-service
)

for module in "${MODULES[@]}"; do
  echo "==> Building ${module}"
  (cd "${ROOT}/${module}" && mvn -q clean package)
  test -f "${ROOT}/${module}/target/${module}.jar"
done

echo "Built and tested ${#MODULES[@]} modules."
