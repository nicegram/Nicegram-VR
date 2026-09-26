#!/usr/bin/env python3
"""Local release check. Receive the server key via environment, never an argument.
Scan unpacked APK entries and changed Git blobs; print only counts and PASS/FAIL.
The key is not copied, hashed, printed or written to a report.
"""
import argparse
import base64
import os
import subprocess
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('--apk', required=True)
parser.add_argument('--git-base', required=True)
args = parser.parse_args()
secret = os.environ.get('NICEGRAM_AUTH_HEADER', '')
if len(secret) < 32:
    raise SystemExit('Required server credential is unavailable; scan NOT_RUN')
patterns = (secret.encode(), secret.encode('utf-16-le'), base64.b64encode(secret.encode()))

def clean(stream):
    tail = b''
    while chunk := stream.read(1024 * 1024):
        data = tail + chunk
        if any(pattern in data for pattern in patterns):
            return False
        tail = data[-max(map(len, patterns)):]
    return True

with zipfile.ZipFile(args.apk) as apk:
    entries = apk.infolist()
    for entry in entries:
        with apk.open(entry) as stream:
            if not clean(stream):
                raise SystemExit('FAIL: server credential found in APK')
paths = subprocess.check_output(['git', 'diff', '--name-only', '--diff-filter=ACMR', args.git_base, 'HEAD', '-z']).split(b'\0')
paths = [p for p in paths if p]
for path in paths:
    blob = subprocess.check_output(['git', 'show', 'HEAD:' + os.fsdecode(path)])
    if any(pattern in blob for pattern in patterns):
        raise SystemExit('FAIL: server credential found in changed Git blob')
print(f'PASS: server credential absent from {len(entries)} APK entries and {len(paths)} changed Git blobs')
