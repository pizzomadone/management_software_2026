"""Optional signing; private material exists only in a temporary directory."""
import base64
import glob
import os
from pathlib import Path
import secrets
import subprocess
import sys
import tempfile

stage = sys.argv[1]
assert stage in ('app', 'installer')
windows = sys.platform == 'win32'
prefix = 'WINDOWS' if windows else 'MACOS'
certificate = os.environ.get(prefix + '_CERTIFICATE_P12', '')
password = os.environ.get(prefix + '_CERTIFICATE_PASSWORD', '')
if not certificate:
    if password or (not windows and os.environ.get('MACOS_SIGNING_IDENTITY')):
        raise SystemExit('Incomplete signing credentials')
    print('Signing certificate not configured: unsigned test build')
    sys.exit(0)

def run(*args):
    # Never echo commands containing certificate passwords.
    result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        raise SystemExit(f'{Path(args[0]).name} failed (exit {result.returncode}); check signing credentials and certificate validity')
    return result.stdout

with tempfile.TemporaryDirectory(prefix='warestat-sign-') as folder:
    cert = Path(folder) / 'certificate.p12'
    cert.write_bytes(base64.b64decode(certificate, validate=True))
    cert.chmod(0o600)
    if windows:
        candidates = glob.glob(os.environ.get('ProgramFiles(x86)', r'C:\Program Files (x86)')
                               + r'\Windows Kits\10\bin\*\x64\signtool.exe')
        if not candidates: raise SystemExit('Windows SDK signtool.exe is required for signing')
        tool = sorted(candidates)[-1]
        targets = [Path('dist/WareStat/WareStat.exe')] if stage == 'app' else list(Path('dist/installers').glob('*.exe'))
        if not targets: raise SystemExit('No signing target')
        for target in targets:
            run(tool, 'sign', '/fd', 'SHA256', '/f', str(cert), '/p', password,
                '/tr', 'http://timestamp.digicert.com', '/td', 'SHA256', str(target))
            run(tool, 'verify', '/pa', str(target))
    else:
        identity = os.environ.get('MACOS_SIGNING_IDENTITY', '')
        if not identity.startswith('Developer ID Application:'):
            raise SystemExit('MACOS_SIGNING_IDENTITY must be the full Developer ID Application identity')
        keychain = str(Path(folder) / 'signing.keychain-db')
        keypass = secrets.token_hex(24)
        try:
            run('security', 'create-keychain', '-p', keypass, keychain)
            run('security', 'set-keychain-settings', '-lut', '21600', keychain)
            run('security', 'unlock-keychain', '-p', keypass, keychain)
            run('security', 'import', str(cert), '-k', keychain, '-P', password, '-T', '/usr/bin/codesign')
            run('security', 'set-key-partition-list', '-S', 'apple-tool:,apple:,codesign:', '-s', '-k', keypass, keychain)
            def sign(path, executable=False):
                args = ['codesign', '--force', '--timestamp', '--options', 'runtime', '--keychain', keychain, '--sign', identity]
                if executable: args += ['--entitlements', 'packaging/macos-entitlements.plist']
                run(*args, str(path))
            if stage == 'app':
                app = Path('dist/WareStat.app')
                # Sign every Mach-O before enclosing bundles, without --deep signing.
                for path in sorted(app.rglob('*'), key=lambda p: len(p.parts), reverse=True):
                    if path.is_file() and not path.is_symlink():
                        kind = run('file', '-b', str(path)).decode()
                        if 'Mach-O' in kind: sign(path, 'executable' in kind)
                sign(app / 'Contents/runtime')
                sign(app, True)
                run('codesign', '--verify', '--deep', '--strict', str(app))
            else:
                apple = [os.environ.get(k, '') for k in ('APPLE_ID', 'APPLE_TEAM_ID', 'APPLE_APP_PASSWORD')]
                if not all(apple): raise SystemExit('Signed macOS releases require all notarization credentials')
                targets = list(Path('dist/installers').glob('*.dmg'))
                if not targets: raise SystemExit('No DMG to notarize')
                run('xcrun', 'notarytool', 'store-credentials', 'warestat', '--keychain', keychain,
                    '--apple-id', apple[0], '--team-id', apple[1], '--password', apple[2])
                for target in targets:
                    sign(target)
                    result = run('xcrun', 'notarytool', 'submit', str(target), '--keychain', keychain,
                                 '--keychain-profile', 'warestat', '--wait', '--output-format', 'json')
                    import json
                    if json.loads(result).get('status') != 'Accepted':
                        raise SystemExit('Apple notarization was not accepted')
                    run('xcrun', 'stapler', 'staple', str(target))
                    run('xcrun', 'stapler', 'validate', str(target))
        finally:
            subprocess.run(['security', 'delete-keychain', keychain], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
