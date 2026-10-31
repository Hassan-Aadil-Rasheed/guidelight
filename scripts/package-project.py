"""Build an organized laptop-transfer ZIP and verify every archived file."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import stat
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
EXCLUDED = {'.git', '.gradle', '.kotlin', '.idea', '.tools', 'build', '.cxx',
            '.externalNativeBuild', '__pycache__', 'output', 'captures'}
ROOT_FILES = ('.gitignore', '.gitattributes', 'README.md', 'AGENTS.md',
              'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat')
SOURCE_DIRS = ('app', 'docs', 'gradle', 'scripts', 'third_party', 'migration')


def digest(path):
    result = hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            result.update(chunk)
    return result.hexdigest()


def allowed(name):
    path = Path(name)
    return (not any(part in EXCLUDED for part in path.parts)
            and path.name not in {'local.properties', 'google-maps.local.properties',
                                  'credentials.json', 'token.json', 'auth.json'}
            and not path.name.startswith('.env')
            and path.suffix.lower() not in {'.keystore', '.jks', '.pem', '.p12', '.pfx',
                                            '.pyc', '.apk', '.aab', '.zip'}
            and (path.as_posix() in ROOT_FILES or path.parts[0] in SOURCE_DIRS))


def source_files():
    if (ROOT / '.git').is_dir():
        result = subprocess.run(['git', '-c', f'safe.directory={ROOT.as_posix()}', 'ls-files', '-z'],
                                cwd=ROOT, capture_output=True, check=True)
        names = result.stdout.decode('utf-8').rstrip('\0').split('\0')
        return sorted(name for name in names if name and allowed(name))
    names = [name for name in ROOT_FILES if (ROOT / name).is_file()]
    for folder in SOURCE_DIRS:
        for current, directories, filenames in os.walk(ROOT / folder, followlinks=False):
            directories[:] = sorted(d for d in directories if d not in EXCLUDED)
            for filename in filenames:
                relative = (Path(current) / filename).relative_to(ROOT).as_posix()
                if allowed(relative):
                    names.append(relative)
    return sorted(set(names))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', help='New ZIP filename in the project directory')
    parser.add_argument('--include-signing-key', action='store_true',
                        help='Private laptop transfer only: include the existing development signing key')
    args = parser.parse_args()
    build = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8-sig')
    version = re.search(r'versionName\s*=\s*"([^"]+)"', build).group(1)
    code = int(re.search(r'versionCode\s*=\s*(\d+)', build).group(1))
    bundle = f'GuideLight-{version}'
    output = (ROOT / (args.output or f'{bundle}-Laptop.zip')).resolve()
    if output.parent != ROOT or output.suffix.lower() != '.zip' or output.exists():
        raise SystemExit('Choose a new ZIP filename inside the project directory.')
    candidates = [ROOT / f'{bundle}.apk', ROOT.parent / 'Install' / f'{bundle}.apk',
                  ROOT / 'app/build/outputs/apk/debug/app-debug.apk']
    apk = next((p for p in candidates if p.is_file()), None)
    if apk is None:
        raise SystemExit('Build the latest APK before packaging: gradlew.bat assembleDebug')
    entries = {f'Project/{name}': ROOT / name for name in source_files()}
    required = ('app/build.gradle.kts', 'app/src/main/AndroidManifest.xml',
                'app/src/main/java/com/guidelight/app/MainActivity.kt',
                'app/src/main/jniLibs/arm64-v8a/libNativeRTABMap.so',
                'app/src/main/jniLibs/arm64-v8a/libguidelight_lfm_server.so',
                'gradle/wrapper/gradle-wrapper.jar', 'gradlew', 'gradlew.bat')
    for name in required:
        if f'Project/{name}' not in entries:
            raise SystemExit(f'Missing required project file: {name}')
    apk_hash = digest(apk)
    exported_checksum = apk.with_suffix('.apk.sha256')
    if exported_checksum.is_file() and exported_checksum.read_text().split()[0].lower() != apk_hash:
        raise SystemExit('The APK does not match its exported checksum.')
    entries[f'Install/{bundle}.apk'] = apk
    entries[f'Install/{bundle}.apk.sha256'] = f'{apk_hash}  {bundle}.apk\n'.encode()
    for folder in (ROOT / 'output/pdf', ROOT.parent / 'Guides/Print'):
        if folder.is_dir():
            for pdf in folder.glob('GuideLight*.pdf'):
                entries[f'Guides/Print/{pdf.name}'] = pdf
    private_note = ''
    if args.include_signing_key:
        key = ROOT / 'migration/debug.keystore'
        if not key.is_file():
            raise SystemExit('The original development signing key is not present.')
        entries['Project/migration/debug.keystore'] = key
        private_note = ('This private transfer includes the original development signing key at '
                        '`Project/migration/debug.keystore`. Keep this ZIP private; do not upload '
                        'it to a public repository. The key is excluded from Git.\n')
        entries['Project/migration/README.md'] = ('# Local development identity\n\n' + private_note +
            '\nThe Gradle project automatically uses this key when it is present, allowing '
            'update installs on the existing development phone.\n').encode()
    start = f'''# GuideLight {version} — start here

Your organized laptop transfer contains:

- **Project/** — open this folder in Android Studio. Includes source, tests, assets, native libraries and developer documentation.
- **Install/{bundle}.apk** — the latest tested Android app, with its SHA-256 checksum.
- **Guides/SETUP-NEW-LAPTOP.md** — laptop setup, build and phone-install steps.
- **Guides/Print/** — the printable indoor reference marker.
- **MANIFEST.json** — every file's SHA-256 checksum and size.

## To use the app

Copy the APK from **Install** to your Android phone and install it as an update.
For voice control, use Wispr Flow and your GuideLight button: one short press to speak, another to send.

## To continue development

Extract the whole ZIP to a short path such as `D:/Projects/{bundle}`.
Open **{bundle}/Project** in Android Studio, then follow the setup guide.

{private_note}
SDKs, JDKs, caches, old APKs, temporary logs, account credentials and phone home-map data are not included.
'''
    setup = f'''# Set up GuideLight {version} on another laptop

## 1. Extract and open

Extract the complete ZIP. In Android Studio, open the **Project** folder inside
**{bundle}**, rather than the outer ZIP folder.

## 2. Prepare the build tools

Use JDK 17 and Android SDK Platform 35. Install Android SDK Build-Tools 35.0.0
and Platform-Tools through Android Studio's SDK Manager. Allow Gradle sync to
fetch the project's dependencies. The Gradle wrapper is included, so a separate
Gradle installation is not required. Android Studio creates `local.properties`
for the SDK location on this laptop; no old computer paths are bundled.

## 3. Build and check

Open a terminal in **Project** and run on Windows:

```powershell
.\\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

On macOS/Linux, use `./gradlew :app:testDebugUnitTest :app:assembleDebug`.
The built APK is `Project/app/build/outputs/apk/debug/app-debug.apk`.
The packaged native libraries target ARM64 Android phones; real camera and AR
checks require a supported physical phone.

## 4. Install on your phone

The ready-to-install APK is already in **Install/{bundle}.apk**.
For an ADB update from the **Project** folder, enable USB debugging and run:

```powershell
adb install -r ../Install/{bundle}.apk
```

An update preserves the existing phone app's data. Avoid uninstalling it if you
want to keep saved rooms, downloaded models and settings.

## 5. Voice and indoor tools

Wispr Flow and GuideLight button control remain separate Android accessibility
services. Use two short button presses; holding the remote can power it off.
The source documentation is in **Project/docs**. The optional marker printout is
in **Guides/Print**. Saved home maps, model downloads, Wispr login and encrypted
Cloudflare settings remain on the phone and are not exported in this ZIP.

## Signing identity

{private_note or 'No signing key is included. A new laptop normally creates a different local debug signing key. To update an existing development install without replacing its data, separately restore its original key to Project/migration/debug.keystore.'}

## Integrity

The outer ZIP has a sibling `.zip.sha256` file. **MANIFEST.json** records every
archived file's size and SHA-256. The APK also has a separate checksum in **Install**.
'''
    entries['START-HERE.md'] = start.encode()
    entries['Guides/SETUP-NEW-LAPTOP.md'] = setup.encode()
    manifest = {'project': 'GuideLight', 'version': version, 'version_code': code,
                'purpose': 'Organized laptop development transfer',
                'includes_original_debug_signing_key': args.include_signing_key,
                'excluded': ['SDK/JDK installations', 'build and Gradle caches', 'Git metadata',
                             'old APKs', 'temporary diagnostics', 'phone maps and account credentials'],
                'files': []}
    with zipfile.ZipFile(output, 'x', compression=zipfile.ZIP_DEFLATED,
                         compresslevel=6, allowZip64=True) as archive:
        for name, source in sorted(entries.items()):
            info = zipfile.ZipInfo(f'{bundle}/{name}')
            info.create_system = 3
            info.external_attr = (stat.S_IFREG | (0o755 if name == 'Project/gradlew' else 0o644)) << 16
            info.compress_type = zipfile.ZIP_STORED if Path(name).suffix.lower() in {
                '.apk', '.jpg', '.jpeg', '.png', '.jar', '.pdf'} else zipfile.ZIP_DEFLATED
            sha = hashlib.sha256()
            size = 0
            if isinstance(source, Path):
                if source.is_symlink() or (not source.resolve().is_relative_to(ROOT)
                        and source != apk and source.parent != ROOT.parent / 'Guides/Print'):
                    raise RuntimeError(f'Unexpected external link: {name}')
                with source.open('rb') as stream, archive.open(info, 'w', force_zip64=True) as target:
                    for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                        target.write(chunk); sha.update(chunk); size += len(chunk)
            else:
                with archive.open(info, 'w') as target:
                    target.write(source)
                sha.update(source); size = len(source)
            manifest['files'].append({'path': name, 'bytes': size, 'sha256': sha.hexdigest()})
        archive.writestr(f'{bundle}/MANIFEST.json', json.dumps(manifest, indent=2))
    with zipfile.ZipFile(output) as archive:
        stored = json.loads(archive.read(f'{bundle}/MANIFEST.json'))
        assert len(archive.namelist()) == len(stored['files']) + 1
        for item in stored['files']:
            content = hashlib.sha256()
            size = 0
            with archive.open(f"{bundle}/{item['path']}") as stream:
                for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                    content.update(chunk); size += len(chunk)
            assert size == item['bytes'] and content.hexdigest() == item['sha256'], item['path']
        assert archive.getinfo(f'{bundle}/Project/gradlew').external_attr >> 16 & 0o111
        assert [n for n in archive.namelist() if n.endswith('.apk')] == [f'{bundle}/Install/{bundle}.apk']
    checksum = digest(output)
    output.with_suffix('.zip.sha256').write_text(f'{checksum}  {output.name}\n', encoding='ascii')
    print(json.dumps({'zip': str(output), 'files': len(entries),
                      'size_mb': round(output.stat().st_size / 1024 / 1024, 1),
                      'sha256': checksum, 'integrity': 'Every file CRC and SHA-256 verified',
                      'private_signing_key_included': args.include_signing_key}, indent=2))


if __name__ == '__main__':
    main()
