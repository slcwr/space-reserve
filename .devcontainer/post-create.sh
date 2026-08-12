#!/usr/bin/env bash
set -euo pipefail

# named volume は初回作成時に root 所有になるため、開発ユーザーに寄せる。
sudo chown -R "$(id -u):$(id -g)" "${GRADLE_USER_HOME:-$HOME/.gradle}"

echo "==> docker access check"
docker version --format '{{.Server.Version}}'

echo "==> warming up Gradle"
./gradlew --no-daemon compileJava

echo "==> ready. './gradlew bootRun' for compose MySQL, './gradlew bootTestRun' for Testcontainers."
