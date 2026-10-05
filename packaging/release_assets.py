"""Validate version tags and customer-facing installer/checksum pairs."""
import hashlib
import os
from pathlib import Path
import re
import sys

version = next(line.split('=', 1)[1].strip() for line in Path('build.properties').read_text().splitlines()
               if line.startswith('app.version='))
# Installer 1.3 and release tag v1.3.0 refer to the same numeric version.
def numeric(value):
    if not re.fullmatch(r'\d+\.\d+(?:\.\d+)?', value):
        raise SystemExit('Release versions must have two or three numeric components')
    return tuple(int(n) for n in (value.split('.') + ['0'])[:3])

if sys.argv[1] == 'tag':
    tag = os.environ['GITHUB_REF_NAME']
    if not tag.startswith('v') or numeric(tag[1:]) != numeric(version):
        raise SystemExit('Release tag does not match build.properties')
else:
    root = Path(sys.argv[2])
    files = list(root.iterdir())
    if not files or any(not p.is_file() for p in files): raise SystemExit('Invalid public output directory')
    pattern = re.compile(r'WareStat-' + re.escape(version) + r'-(windows-x64\.exe|macos-(arm64|x64)\.dmg)')
    installers = [p for p in files if pattern.fullmatch(p.name)]
    expected = {p.name for p in installers} | {p.name + '.sha256' for p in installers}
    if expected != {p.name for p in files} or not installers: raise SystemExit('Unexpected or missing public files')
    if len(sys.argv) > 3 and sys.argv[3] == 'all' and len(installers) != 3:
        raise SystemExit('Release requires all three platform installers')
    for p in installers:
        actual = hashlib.sha256(p.read_bytes()).hexdigest()
        recorded = (root / (p.name + '.sha256')).read_text().strip()
        if recorded != f'{actual}  {p.name}': raise SystemExit('Checksum mismatch: ' + p.name)
        print(f'{p.name}: {p.stat().st_size} bytes; SHA-256 verified')
        if os.environ.get('GITHUB_STEP_SUMMARY'):
            with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as out:
                out.write(f'\n{p.name}: {p.stat().st_size} bytes; SHA-256 verified\n')
