#!/usr/bin/env bash
# Run ONCE while online. Fills ~/.m2 so that `mvn -o verify` works with the network off.
set -euo pipefail

echo "1/2  resolving dependencies and plugins..."
mvn -q dependency:go-offline

# go-offline misses plugin dependencies that only resolve during a real build, so do one.
# Failures here are expected while the exercises are unfinished; we only want the jars.
echo "2/2  running a real build to catch the rest (failures here are expected)..."
mvn -q verify -Dmaven.test.failure.ignore=true || true

echo
echo "Done. Verify with: mvn -o verify"
