#!/usr/bin/env python3
"""Extract Meta's prohibited Android permission names from the published page.

Why this exists as a file rather than a heredoc inside the workflow: the release
workflow checks every build against Meta's LIVE list, because a hardcoded copy of
that list goes stale in silence and a stale copy is a failed store upload that
nobody predicted. Keeping the parser here means it can be read, tested and fixed
without editing YAML.

Usage:  extract_prohibited.py <saved-html>  > prohibited.txt

Prints one permission constant per line, sorted. Prints nothing and exits 0 when
the page could not be read — the caller treats an empty list as "the check did
not run" and says so, rather than as "nothing is prohibited".
"""

import html
import io
import re
import sys

# Permission constants are SCREAMING_SNAKE with at least one underscore. The page
# is ordinary marketing HTML around a table, so anything matching that shape and
# not in the noise set below is a permission name.
CONSTANT = re.compile(r"\b([A-Z][A-Z0-9]{2,}(?:_[A-Z0-9]+)+)\b")

# Shapes that match the pattern but are not permissions.
NOISE = {
    "UTF_8",
    "NEXT_PUBLIC",
    "DOM_CONTENT",
}


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: extract_prohibited.py <saved-html>", file=sys.stderr)
        return 2
    try:
        raw = io.open(sys.argv[1], encoding="utf-8", errors="replace").read()
    except OSError as exc:
        print(f"could not read {sys.argv[1]}: {exc}", file=sys.stderr)
        return 0

    found = set(CONSTANT.findall(html.unescape(raw)))
    for name in sorted(n for n in found if n not in NOISE and len(n) > 5):
        print(name)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
