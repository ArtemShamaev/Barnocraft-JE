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

# The packaged target/ directory is published intentionally. Saves and
# downloaded JDK installers remain excluded by .gitignore.
git add -A .

if git diff --cached --quiet; then
    echo "Нет изменений для отправки."
    exit 0
fi

git commit -m "$COMMIT_MESSAGE"
# The repository history was cleaned from the accidentally committed JDK;
# force-with-lease is required once to replace the old remote history while
# still refusing to overwrite a remote update we have not fetched.
git push --force-with-lease -u origin "$(git branch --show-current)"
echo "Изменения отправлены в $REMOTE_URL"
