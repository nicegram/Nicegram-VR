#!/usr/bin/env python3
"""Read permission constants from Meta's permission TABLE, never scripts/navigation.

A missing/empty/unrecognized table fails closed. Fetch the live page separately and
retain its digest with the release receipt; this file is not a cached policy list.
"""
from html.parser import HTMLParser
import re
import sys
from pathlib import Path


class PermissionTable(HTMLParser):
    def __init__(self):
        super().__init__()
        self.table = self.code = 0
        self.names = set()

    def handle_starttag(self, tag, attrs):
        if tag == 'table': self.table += 1
        if tag == 'code': self.code += 1

    def handle_endtag(self, tag):
        if tag == 'table': self.table -= 1
        if tag == 'code': self.code -= 1

    def handle_data(self, text):
        value = text.strip()
        if self.table > 0 and self.code > 0 and re.fullmatch(r'[A-Z][A-Z0-9]+(?:_[A-Z0-9]+)+', value):
            self.names.add(value)


def extract(raw):
    parser = PermissionTable()
    parser.feed(raw)
    if not {'READ_CONTACTS', 'INSTALL_PACKAGES', 'CALL_PHONE'} <= parser.names:
        raise ValueError('Meta permission table is missing or changed; no permission verdict')
    return sorted(parser.names)


def main():
    try:
        if len(sys.argv) != 2: raise ValueError('usage: extract_prohibited.py <saved-html>')
        print('\n'.join(extract(Path(sys.argv[1]).read_text())))
        return 0
    except (OSError, ValueError) as error:
        print(error, file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
