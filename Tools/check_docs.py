#!/usr/bin/env python3
"""Check that the documentation still describes this repository.

Why this exists: every document here carries file paths, `file:line` citations and links to other
documents, and all three rot silently. A path that moved, a line number that drifted past the end
of its file, a link to a document that was renamed — none of them break a build, and each one
turns a document that reads as authoritative into one that is wrong in a way nobody notices.

    python3 Tools/check_docs.py          # exits 1 and names every failure
    python3 Tools/check_docs.py --list   # prints what it checked, for when a pass looks too easy

What it checks, and nothing else:

  * every repository path named in a document exists
  * every `file:line` citation points at a file that HAS that line
  * every relative Markdown link resolves to a file
  * a short list of counts that documents state and that can be recomputed

What it deliberately does NOT check: whether the line still says what the document claims it
says. That needs a human or a much cleverer tool, and a check that pretends to do it would be
worse than one that admits it does not.
"""

from __future__ import annotations

import argparse
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

DOCS = sorted(
    p for p in [
        *ROOT.glob("docs/*.md"),
        *ROOT.glob("store/*.md"),
        *ROOT.glob("store/assets/*.md"),
        *ROOT.glob("language-pack/*.md"),
        ROOT / "README.md",
        ROOT / "CONTRIBUTING.md",
    ]
    if p.is_file()
)

# A path that looks like it belongs to this repository. Deliberately narrow: it must start with a
# known top-level directory, so prose like "a build.gradle somewhere" is not mistaken for a claim.
TOP = r"(?:TMessagesProj|TMessagesProj_App[A-Za-z]*|docs|store|language-pack|Tools|\.github)"
PATH_RE = re.compile(rf"(?<![\w/]){TOP}/[A-Za-z0-9_./-]*[A-Za-z0-9_]")
LINE_RE = re.compile(rf"((?:{TOP}/)?[A-Za-z0-9_./-]+\.(?:java|gradle|xml|py|yml|md)):(\d+)")
LINK_RE = re.compile(r"\[[^\]]*\]\(([^)#\s]+(?:\.md|\.py|\.html|\.xml|\.gradle))(?:#[^)]*)?\)")

# Files named by their basename alone in prose, resolved by searching the tree. Keeps the check
# honest about the ones that genuinely live in one place.
SEARCHABLE_SUFFIXES = (".java", ".gradle", ".xml", ".py", ".yml")


def find_by_name(name: str) -> pathlib.Path | None:
    """One file with that basename, or None if zero or many."""
    hits = [p for p in ROOT.rglob(name) if ".git" not in p.parts and "build" not in p.parts]
    return hits[0] if len(hits) == 1 else None


def check_paths(doc: pathlib.Path, text: str, failures: list[str], checked: list[str]) -> None:
    for match in PATH_RE.finditer(text):
        raw = match.group(0).rstrip(".,;:")
        target = ROOT / raw
        checked.append(f"path {raw}")
        if target.exists():
            continue
        # `TMessagesProj_AppQuest/.../VrDensity.java` is an ellipsis a reader understands, not a
        # broken path. It is still a claim, so it is resolved by basename rather than waved past:
        # if exactly one file in the tree has that name, the claim holds.
        if "/.../" in raw or raw.endswith("/..."):
            name = pathlib.PurePath(raw).name
            if name != "..." and find_by_name(name) is not None:
                continue
            failures.append(
                f"{doc.relative_to(ROOT)}: no file named {name} anywhere — {raw}"
            )
            continue
        # A directory named without a trailing slash, or a glob-ish mention, is still fine.
        if target.parent.exists() and any(target.parent.glob(target.name + "*")):
            continue
        failures.append(f"{doc.relative_to(ROOT)}: path does not exist — {raw}")


def check_lines(doc: pathlib.Path, text: str, failures: list[str], checked: list[str]) -> None:
    for match in LINE_RE.finditer(text):
        raw, number = match.group(1), int(match.group(2))
        target = ROOT / raw
        if not target.is_file() and raw.endswith(SEARCHABLE_SUFFIXES):
            found = find_by_name(pathlib.PurePath(raw).name)
            if found is None:
                continue  # ambiguous or absent; check_paths owns full paths
            target = found
        if not target.is_file():
            continue
        checked.append(f"{raw}:{number}")
        total = sum(1 for _ in target.open(encoding="utf-8", errors="replace"))
        if number > total:
            failures.append(
                f"{doc.relative_to(ROOT)}: {raw}:{number} — the file has only {total} lines"
            )


def check_links(doc: pathlib.Path, text: str, failures: list[str], checked: list[str]) -> None:
    for match in LINK_RE.finditer(text):
        href = match.group(1)
        if href.startswith(("http://", "https://", "mailto:")):
            continue
        target = (doc.parent / href).resolve()
        checked.append(f"link {href} (from {doc.name})")
        if not target.exists():
            failures.append(f"{doc.relative_to(ROOT)}: link does not resolve — {href}")


def check_counts(failures: list[str], checked: list[str]) -> None:
    """The few numbers documents state that can be recomputed here."""

    def stated(pattern: str, *docs: str) -> list[tuple[str, int]]:
        """Every number a document states with this shape, as (document, number)."""
        out = []
        for name in docs:
            path = ROOT / name
            if not path.is_file():
                continue
            for m in re.finditer(pattern, path.read_text(encoding="utf-8")):
                digits = next((g for g in m.groups() if g and g.isdigit()), None)
                if digits is not None:
                    out.append((name, int(digits)))
        return out

    # Permissions removed in the headset manifest.
    manifest = ROOT / "TMessagesProj_AppQuest/src/main/AndroidManifest.xml"
    if manifest.is_file():
        removed = len(re.findall(r'uses-permission[^>]*tools:node="remove"', manifest.read_text(encoding="utf-8")))
        checked.append(f"permissions removed in the quest manifest = {removed}")
        # The documents say "eight" in words rather than digits, so the count is pinned here
        # rather than parsed out of prose: change one and this names the other.
        if removed != 8:
            failures.append(
                "the quest manifest removes "
                f"{removed} permissions; the documents say eight "
                "(docs/horizon-store-readiness.md, docs/audit-2026-09-19.md)"
            )

    # Language-pack key parity is owned by LanguagePackParityTest; what is checked here is the
    # count the documents quote, because that is the part a test cannot see.
    en = ROOT / "TMessagesProj_AppQuest/src/main/res/values/strings_vr.xml"
    ru = ROOT / "language-pack/strings_vr.ru.xml"
    if en.is_file() and ru.is_file():
        def keys(p: pathlib.Path) -> int:
            body = p.read_text(encoding="utf-8")
            n = len(re.findall(r'<string name="[^"]+"(?![^>]*translatable="false")', body))
            return n + len(re.findall(r"<plurals name=", body))
        n_en, n_ru = keys(en), keys(ru)
        checked.append(f"language-pack keys en={n_en} ru={n_ru}")
        if n_en != n_ru:
            failures.append(f"language pack: {n_en} English keys against {n_ru} Russian")
        # Bold is the live claim; plain prose may be recounting history ("the old text said 72
        # keys", "the 47 keys that existed before"), and flagging those would teach everyone to
        # ignore this check.
        for name, claimed in stated(r"\*\*(\d+) keys\*\*", "docs/plan.md", "language-pack/README.md"):
            if claimed != n_en:
                failures.append(f"{name}: claims **{claimed} keys**; there are {n_en}")

    # The unit-test count, when a test run is on disk to compare against.
    results = sorted((ROOT / "TMessagesProj_AppQuest/build/test-results/testQuestDebugUnitTest").glob("TEST-*.xml"))
    if results:
        total = sum(int(re.search(r'tests="(\d+)"', p.read_text(encoding="utf-8")).group(1)) for p in results)
        checked.append(f"unit tests on disk = {total} in {len(results)} classes")
        for name, claimed in stated(r"(\d+) (?:unit )?tests? across", "README.md", "docs/audit-2026-09-19.md"):
            if claimed != total:
                failures.append(
                    f"{name}: says {claimed} tests; the last run on disk has {total}. "
                    "Re-run the suite or correct the document."
                )


# Addresses that appear as ILLUSTRATIONS and must not resolve — the dictation documents use them
# to show what the endpoint check accepts and refuses.
EXAMPLE_HOSTS = ("example.com", "example.org", "127.0.0.1", "localhost", "[::1]", "asr.example")


def check_urls(failures: list[str], checked: list[str]) -> None:
    """Opt-in, because it needs a network and CI should not go red over a blip."""
    seen: set[str] = set()
    for doc in DOCS:
        for url in re.findall(r"https?://[A-Za-z0-9._~:/?#@!$&()*+,;=%-]+", doc.read_text(encoding="utf-8")):
            seen.add(url.rstrip(".,;:)`"))
    for url in sorted(seen):
        if any(host in url for host in EXAMPLE_HOSTS):
            continue
        checked.append(f"url {url}")
        try:
            out = subprocess.run(
                ["curl", "-s", "-o", "/dev/null", "-w", "%{http_code}", "-L", "--max-time", "20", url],
                capture_output=True, text=True, timeout=40,
            ).stdout.strip()
        except Exception as exc:  # network trouble is not a documentation defect
            print(f"  could not reach {url}: {exc}", file=sys.stderr)
            continue
        if out not in {"200", "301", "302"}:
            failures.append(f"url answers {out} — {url}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--list", action="store_true", help="print everything that was checked")
    parser.add_argument("--urls", action="store_true", help="also fetch every URL (needs a network)")
    args = parser.parse_args()

    if not DOCS:
        print("no documents found — the paths this script walks have moved", file=sys.stderr)
        return 2

    failures: list[str] = []
    checked: list[str] = []
    for doc in DOCS:
        text = doc.read_text(encoding="utf-8")
        check_paths(doc, text, failures, checked)
        check_lines(doc, text, failures, checked)
        check_links(doc, text, failures, checked)
    check_counts(failures, checked)
    if args.urls:
        check_urls(failures, checked)

    if args.list:
        for item in checked:
            print(f"  checked  {item}")

    print(f"{len(DOCS)} documents, {len(checked)} checkable claims")
    if failures:
        print(f"\n{len(failures)} claims are no longer true:\n")
        for failure in sorted(set(failures)):
            print(f"  {failure}")
        return 1
    print("every checkable claim still holds")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
