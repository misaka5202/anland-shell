#!/bin/sh
# Generate the Release body from docs/CHANGELOG.en.md — same shape as
# droidspaces-lkm's release page:
#
#     # Changelog
#
#     ## <X.Y.Z> - <YYYY-MM-DD>
#
#     <the entry for that version in docs/CHANGELOG.en.md, ### sections included>
#
#     ---
#
#     Full history: [docs/CHANGELOG.en.md](<permalink>)
#
# The body is the changelog, verbatim — there is no second description to keep
# in sync.
#
# The version comes from the tag, not from an artifact name: anland-shell.apk
# carries no version.
#
# Usage: release_notes.sh <tag> [repo root]
set -eu

# Drop leading and trailing blank lines (cat -s only squeezes runs in between).
trim() {
    awk 'NF && !started { started = 1 }
         started        { buf[++n] = $0 }
         NF             { last = n }
         END { for (i = 1; i <= last; i++) print buf[i] }'
}

TAG="${1:-}"
ROOT="${2:-.}"

[ -n "$TAG" ] || { echo "::error::usage: release_notes.sh <tag> [repo root]" >&2; exit 1; }

REPO_SLUG="${REPO_SLUG:-misaka5202/anland-shell}"
BRANCH="${BRANCH:-main}"
VER="${TAG#v}"

EN="$ROOT/docs/CHANGELOG.en.md"
[ -f "$EN" ] || { echo "::error::missing $EN" >&2; exit 1; }

# Slice the entry: from "## <X.Y.Z> - " to the next "## ". The heading line
# itself is kept — it is the "## <version> - <date>" section.
ENTRY=$(awk -v v="$VER" '
    $0 ~ "^## " v " - " { found = 1 }
    found && /^## / && $0 !~ "^## " v " - " { exit }
    found { print }
' "$EN" | cat -s | trim)

[ -n "$ENTRY" ] || {
    echo "::error::$EN has no entry for $VER (heading must read \"## $VER - YYYY-MM-DD\")" >&2
    exit 1
}

echo '# Changelog'
echo
echo "$ENTRY"
echo
echo '---'
echo
echo "Full history: [docs/CHANGELOG.en.md](https://github.com/$REPO_SLUG/blob/$BRANCH/docs/CHANGELOG.en.md)"
