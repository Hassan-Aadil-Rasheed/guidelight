"""Fetch only the Android APK from RTAB-Map's published image layer.

No Docker installation/root filesystem extraction, no container execution.
Verifies the layer digest against the saved registry manifest before reading it.
"""
import hashlib
import json
from pathlib import Path
import tarfile
import urllib.request

root = Path(__file__).resolve().parents[1]
work = root / ".tools/mapping-research"
work.mkdir(parents=True, exist_ok=True)
manifest = json.loads((root / "third_party/rtabmap/android-build-manifest.json").read_text(encoding="utf-8-sig"))
layer = manifest["layers"][-1]
digest = layer["digest"]
token_url = "https://auth.docker.io/token?service=registry.docker.io&scope=repository:introlab3it/rtabmap:pull"
with urllib.request.urlopen(token_url) as response:
    token = json.load(response)["token"]
request = urllib.request.Request("https://registry-1.docker.io/v2/introlab3it/rtabmap/blobs/" + digest,
                                 headers={"Authorization": "Bearer " + token})
archive = work / "android-build-layer.tar.gz"
hasher = hashlib.sha256()
with urllib.request.urlopen(request) as response, archive.open("wb") as output:
    while block := response.read(1024 * 1024):
        output.write(block)
        hasher.update(block)
if hasher.hexdigest() != digest.split(":", 1)[1]:
    raise RuntimeError("Layer digest mismatch")
print("Verified layer", digest, flush=True)
found = []
with tarfile.open(archive, "r:gz") as package:
    for member in package:
        if member.isfile() and member.name.endswith(".apk"):
            destination = work / Path(member.name).name
            with package.extractfile(member) as source, destination.open("wb") as output:
                while block := source.read(1024 * 1024):
                    output.write(block)
            found.append({"source": member.name, "file": destination.name,
                          "sha256": hashlib.sha256(destination.read_bytes()).hexdigest(), "bytes": destination.stat().st_size})
(work / "apk-provenance.json").write_text(json.dumps({"layer": digest, "apks": found}, indent=2), encoding="utf-8")
print(json.dumps(found, indent=2), flush=True)
if not found:
    raise RuntimeError("Official build layer contains no APK")
