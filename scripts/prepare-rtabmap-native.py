"""Install pinned native RTAB-Map libraries from the verified official artifact.

ARCore's C/JNI shims come from the existing 1.43 AAR, never from the older APK.
"""
import hashlib
import json
from pathlib import Path
import urllib.request
import zipfile

root = Path(__file__).resolve().parents[1]
artifact = root / '.tools/mapping-research/RTABMap-debug.apk'
expected = '405cf095747f27c5e1890f6c9787f0ca4b420bb2bd7daea749897dc3859bccca'
if hashlib.sha256(artifact.read_bytes()).hexdigest() != expected:
    raise RuntimeError('Official Android artifact hash mismatch')
destination = root / 'app/src/main/jniLibs/arm64-v8a'
destination.mkdir(parents=True, exist_ok=True)
names = ['libNativeRTABMap.so', 'libomp.so', 'libhuawei_arengine_impl.so',
         'libhuawei_arengine_jni.so', 'libhuawei_arengine_ndk.so']
records = []
with zipfile.ZipFile(artifact) as archive:
    for name in names:
        data = archive.read('lib/arm64-v8a/' + name)
        (destination / name).write_bytes(data)
        records.append({'name': name, 'sha256': hashlib.sha256(data).hexdigest()})
assets = root / 'app/src/main/assets/mapping'
assets.mkdir(parents=True, exist_ok=True)
notice = ['RTAB-Map 0.24.2 native engine — local saved-home recognition trial.',
          'Source: https://github.com/introlab/rtabmap/tree/0.24.2',
          'Official published Android build: introlab3it/rtabmap:android30',
          'Layer: sha256:2cd3d36e86d5d7e6c7610db3ec792897f9744e6bbcb5da8a559f62e5e6f6860a',
          'Artifact SHA-256: ' + expected,
          'Native engine is unchanged. This app uses ARCore 1.43 shims from its existing AAR.',
          'Huawei provider libraries are runtime dependencies of the upstream build; this app selects ARCore only.',
          'Other upstream native dependencies include OpenCV, PCL, Eigen, Boost, g2o, SQLite, zlib and LLVM OpenMP.',
          'This local qualification build is not a public distribution release. Complete the dependency licence audit before publication.', '']
with urllib.request.urlopen('https://raw.githubusercontent.com/introlab/rtabmap/0.24.2/LICENSE') as response:
    notice.append(response.read().decode())
(assets / 'NOTICES.txt').write_text('\n'.join(notice), encoding='utf-8')
(assets / 'native-provenance.json').write_text(json.dumps(records, indent=2), encoding='utf-8')
print('Prepared RTAB-Map 0.24.2 arm64 libraries; ARCore dependencies retained from Gradle.')
