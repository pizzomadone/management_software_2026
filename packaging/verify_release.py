"""Inspect the exact jpackage app image and run its bundled Java in isolation."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent.parent
os.chdir(ROOT)
props = dict(line.split('=', 1) for line in Path('build.properties').read_text().splitlines()
             if '=' in line and not line.startswith('#'))
version = props['app.version']
windows = sys.platform == 'win32'
image = Path('dist/WareStat' if windows else 'dist/WareStat.app')
app = image / ('app' if windows else 'Contents/app')
runtime = image / ('runtime' if windows else 'Contents/runtime/Contents/Home')
java = runtime / ('bin/java.exe' if windows else 'bin/java')
jar = app / f'WareStat-{version}-runtime.jar'
assert java.is_file(), f'Missing bundled Java: {java}'
expected = {jar.name, 'webview-1.0.10.jar', 'LICENSE-THIRD-PARTY.txt', 'NOTICE.txt', 'WareStat.cfg', '.jpackage.xml'}
assert {p.name for p in app.iterdir()} <= expected, 'Unexpected app payload'
for name in (jar.name, 'webview-1.0.10.jar', 'LICENSE-THIRD-PARTY.txt', 'NOTICE.txt'):
    assert (app / name).is_file(), f'Missing payload: {name}'
for packed in app.glob('*.jar'):
    with zipfile.ZipFile(packed) as z:
        names = z.namelist()
        leaks = [n for n in names if n.lower().endswith(('.java', '.db', '.sqlite', '.log'))
                 or re.search(r'(proguard-(mapping|seeds|usage|config)|RuntimeSmoke)', n, re.I)]
        assert not leaks, f'Private content in {packed.name}: {leaks}'
        if packed == jar:
            native = [n for n in names if n.startswith('org/sqlite/native/') and n.endswith(('.dll', '.so', '.dylib'))]
            assert len(native) == 1, f'Unexpected SQLite binaries: {native}'
            required = {'MainWindow.class', 'AboutDialog.class', 'org/sqlite/Collation.class',
                        'fonts/DejaVuSans.ttf', 'fonts/DejaVuSans-Bold.ttf', 'fonts/LICENSE-DejaVu.txt',
                        'icons/logo.png', 'LICENSE-THIRD-PARTY.txt', 'NOTICE.txt', 'META-INF/services/java.sql.Driver'}
            assert required <= set(names), f'Missing entries: {required - set(names)}'
            manifest = z.read('META-INF/MANIFEST.MF').decode()
            assert 'Built-By:' not in manifest and 'Built-Date:' not in manifest

# A temporary working directory prevents fallback reads from repository resources.
cp = os.pathsep.join(str(p.resolve()) for p in (jar, app / 'webview-1.0.10.jar'))
with tempfile.TemporaryDirectory(prefix='warestat-smoke-') as tmp:
    subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', cp, '-d', tmp,
                    str(ROOT / 'packaging/RuntimeSmoke.java')], check=True)
    subprocess.run([str(java.resolve()), '-Xverify:all', '-Djava.awt.headless=true',
                    f'-Duser.home={tmp}', '-cp', tmp + os.pathsep + cp, 'RuntimeSmoke', tmp],
                   cwd=tmp, check=True, timeout=90)

# Compare static module requirements to modules actually shipped; JNI and resources
# are checked by the runtime probe above. Optional absent integrations are ignored.
deps = subprocess.check_output(['jdeps', '--multi-release', '21', '--ignore-missing-deps',
                               '--print-module-deps', str(jar)], text=True).strip().splitlines()[-1]
modules = subprocess.check_output([str(java), '--list-modules'], text=True)
shipped = {line.split('@')[0] for line in modules.splitlines()}
assert set(deps.split(',')) <= shipped, f'Missing runtime modules: {deps}'

sizes = {
    'input_jar_bytes': Path(f'build/release/WareStat-{version}-input.jar').stat().st_size,
    'protected_jar_bytes': Path(f'build/release/WareStat-{version}-protected.jar').stat().st_size,
    'platform_jar_bytes': jar.stat().st_size,
    'runtime_bytes': sum(p.stat().st_size for p in runtime.rglob('*') if p.is_file()),
    'app_image_bytes': sum(p.stat().st_size for p in image.rglob('*') if p.is_file()),
}
Path('build/private/size-report.json').write_text(json.dumps(sizes, indent=2) + '\n')
print(json.dumps(sizes, indent=2))
if os.environ.get('GITHUB_STEP_SUMMARY'):
    with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as out:
        out.write('\n| Component | Bytes |\n|---|---:|\n')
        for name, size in sizes.items(): out.write(f'| {name} | {size} |\n')
