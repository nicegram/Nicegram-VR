#!/usr/bin/env python3
"""Validate the signed APK, fail on unavailable evidence, emit a portable JSON receipt."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile
from extract_prohibited import extract


def run(*command):
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode:
        raise ValueError(f'{Path(command[0]).name} failed (exit {result.returncode})')
    return result.stdout


def require(condition, message):
    if not condition: raise ValueError(message)


def manifest_nodes(text):
    result = []
    stack = []
    for line in text.splitlines():
        element = re.match(r'(\s*)E: ([\w-]+)', line)
        if element:
            depth, name = len(element[1]), element[2]
            while stack and stack[-1]['depth'] >= depth: stack.pop()
            node = dict(name=name, depth=depth, attrs={}, parent=stack[-1] if stack else None)
            result.append(node)
            stack.append(node)
        else:
            attr = re.search(r'android:(\w+)\(0x[0-9a-f]+\)=(.*?)(?: \(Raw:.*)?$',line)
            if attr and stack: stack[-1]['attrs'][attr[1]] = attr[2].strip('"')
    return result


def validate(badging, manifest, signature, members, size, prohibited, tag, expected_cert, previous_code):
    require(re.fullmatch(r'v\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?', tag), 'invalid release tag')
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    require(package is not None, 'missing package identity')
    name, code, version = package.groups()
    require(name == 'my.nicegram.vr', 'wrong application id')
    require(int(code) > previous_code, 'versionCode must increase')
    want = tag[1:].split('-')[0]
    require(version == want or version.startswith(want + ' (Telegram '), 'tag/package version mismatch')
    require("application-label:'Nicegram VR'" in badging, 'wrong launcher label')
    require('application-debuggable' not in badging, 'debug APK is not a release')
    require(0 < size < 1_000_000_000, 'APK must be smaller than 1 GB')
    abis = sorted({p.split('/')[1] for p in members if p.startswith('lib/') and p.endswith('.so')})
    require(abis == ['arm64-v8a'], f'wrong native ABIs: {abis}')
    require('Verified using v2 scheme (APK Signature Scheme v2): true' in signature, 'missing verified v2 signature')
    certs = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]+)', signature)
    require(len(certs) == 1 and certs[0].lower() == expected_cert.lower().replace(':',''), 'unexpected signing certificate')
    nodes = manifest_nodes(manifest)
    def named(kind, name=None):
        return next((n for n in nodes if n['name']==kind and
                     (name is None or n['attrs'].get('name')==name)), None)
    root = named('manifest')
    app = named('application')
    launch = named('activity','org.telegram.ui.LaunchActivity')
    require(app and app['attrs'].get('name')=='org.telegram.messenger.QuestApplicationLoader', 'wrong application loader')
    require(app['attrs'].get('debuggable','false')=='false', 'debuggable manifest')
    require(root and root['attrs'].get('installLocation') in ('0','(type 0x10)0x0'), 'installLocation must be auto')
    devices = named('meta-data','com.oculus.supportedDevices')
    require(devices and devices['attrs'].get('value')=='quest3|quest3s', 'supported devices mismatch')
    require(not named('uses-feature','android.hardware.vr.headtracking'), '2D build unexpectedly declares headtracking')
    require(launch and launch['attrs'].get('excludeFromRecents') in ('true','(type 0x12)0xffffffff'), 'launch activity must exclude from recents')
    require(any(n['name']=='layout' and n['parent'] is launch and
                {'defaultWidth','defaultHeight','minWidth','minHeight'} <= n['attrs'].keys() for n in nodes), 'panel layout missing')
    permissions = sorted(set(re.findall(r"^uses-permission[^:]*: name='([^']+)'",badging,re.M)))
    require(permissions, 'no permissions parsed')
    require(prohibited, 'no prohibited-permission evidence')
    bad = sorted(p for p in permissions if p.removeprefix('android.permission.') in prohibited)
    require(not bad, f'prohibited permissions: {bad}')
    sdk = dict(re.findall(r"^(sdkVersion|targetSdkVersion):'(\d+)'",badging,re.M))
    require(29 <= int(sdk.get('sdkVersion',0)) <= 34, 'min SDK outside 2D release range')
    require(32 <= int(sdk.get('targetSdkVersion',0)) <= 36, 'target SDK outside 2D release range')
    return dict(package=name,version_code=int(code),version_name=version,tag=tag,
                bytes=size,abis=abis,signer_sha256=certs[0],signature_v2=True,
                permissions=permissions,prohibited_list_count=len(prohibited),
                prohibited_matches=bad,sdk=sdk,packaging_checks='PASS',
                device_checks='NOT-RUN',store_submission='NOT-SUBMITTED')


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('apk',type=Path)
    p.add_argument('--build-tools',type=Path,required=True)
    p.add_argument('--prohibited-html',type=Path,required=True)
    p.add_argument('--tag',required=True)
    p.add_argument('--expected-cert-sha256',required=True)
    p.add_argument('--previous-code',type=int,required=True)
    p.add_argument('--output',type=Path,required=True)
    args=p.parse_args()
    try:
        aapt=str(args.build_tools/'aapt2')
        badge=run(aapt,'dump','badging',str(args.apk))
        manifest=run(aapt,'dump','xmltree','--file','AndroidManifest.xml',str(args.apk))
        signature=run(str(args.build_tools/'apksigner'),'verify','--verbose','--print-certs',str(args.apk))
        html=args.prohibited_html.read_bytes()
        with zipfile.ZipFile(args.apk) as apk: members=apk.namelist()
        result=validate(badge,manifest,signature,members,args.apk.stat().st_size,
                        extract(html.decode()),args.tag,args.expected_cert_sha256,args.previous_code)
        result.update(sha256=hashlib.sha256(args.apk.read_bytes()).hexdigest(),
                      checked_at=datetime.now(timezone.utc).isoformat(),
                      prohibited_page_sha256=hashlib.sha256(html).hexdigest(),
                      prohibited_source='https://developers.meta.com/horizon/resources/permissions-prohibited/',
                      source_commit=run('git','rev-parse','HEAD').strip())
        args.output.write_text(json.dumps(result,indent=2)+'\n')
        print(json.dumps({k:v for k,v in result.items() if k!='permissions'},indent=2))
        return 0
    except (ValueError,OSError,zipfile.BadZipFile) as error:
        print('RELEASE CHECK FAILED:',error)
        return 1


if __name__=='__main__': raise SystemExit(main())
