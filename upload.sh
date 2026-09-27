#!/usr/bin/env bash
set -euo pipefail

# Publish the current working tree to the Barnocraft GitHub repository.
ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REMOTE_URL="https://github.com/ArtemShamaev/Barnocraft-JE.git"
COMMIT_MESSAGE="${1:-Update Barnicraft}"

cd "$ROOT_DIR"

if ! command -v git >/dev/null 2>&1; then
    echo "Ошибка: git не найден" >&2
    exit 1
fi

if [ ! -f pom.xml ]; then
    echo "Ошибка: скрипт запущен не из проекта Barnocraft" >&2
    exit 1
fi

git remote get-url origin >/dev/null 2>&1 || git remote add origin "$REMOTE_URL"
git remote set-url origin "$REMOTE_URL"

# Build artifacts, saves and downloaded JDK installers do not belong in Git.
git add -A
git reset -- '*.exe' 'target/' 'saves/' 2>/dev/null || true

if git diff --cached --quiet; then
    echo "Нет изменений для отправки."
    exit 0
fi

git commit -m "$COMMIT_MESSAGE"
git push -u origin "$(git branch --show-current)"
echo "Изменения отправлены в $REMOTE_URL"
