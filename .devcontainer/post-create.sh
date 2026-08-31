#!/usr/bin/env bash
set -euo pipefail

# named volume は初回作成時に root 所有になるため、開発ユーザーに寄せる。
sudo chown -R "$(id -u):$(id -g)" "${GRADLE_USER_HOME:-$HOME/.gradle}"

echo "==> docker access check"
docker version --format '{{.Server.Version}}'

echo "==> warming up Gradle"
# wrapper は backend/ の中にある。ルートに Gradle のファイルは置いていない。
(cd backend && ./gradlew --no-daemon compileJava)

echo "==> installing frontend dependencies"
# node_modules はイメージにもボリュームにも残らないため、作成のたびに入れ直す。
npm --prefix frontend install

echo "==> ready."
echo "    backend:  cd backend && ./gradlew bootRun   (bootTestRun for Testcontainers)"
echo "    frontend: npm --prefix frontend run dev     (needs bootRun for /api)"
