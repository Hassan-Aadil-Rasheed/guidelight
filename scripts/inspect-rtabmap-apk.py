"""Read the official Android artifact's ABI and native library dependencies."""
from pathlib import Path
import struct
import zipfile

root = Path(__file__).resolve().parents[1]
with zipfile.ZipFile(root / '.tools/mapping-research/RTABMap-debug.apk') as apk:
    print(apk.read('res/raw/info.txt').decode())
    for member in apk.infolist():
        if not member.filename.startswith('lib/arm64-v8a/'):
            continue
        data = apk.read(member)
        if data[:5] != b'\x7fELF\x02':
            continue
        phoff = struct.unpack_from('<Q', data, 32)[0]
        phsize, phnum = struct.unpack_from('<HH', data, 54)
        segments = [struct.unpack_from('<IIQQQQQQ', data, phoff + i * phsize) for i in range(phnum)]
        dynamic = next(p for p in segments if p[0] == 2)
        entries = [struct.unpack_from('<QQ', data, i) for i in range(dynamic[2], dynamic[2] + dynamic[5], 16)]
        table_addr = next(value for kind, value in entries if kind == 5)
        load = next(p for p in segments if p[0] == 1 and p[3] <= table_addr < p[3] + p[5])
        table = load[2] + table_addr - load[3]
        dependencies = []
        for kind, value in entries:
            if kind == 1:
                start = table + value
                dependencies.append(data[start:data.index(0, start)].decode())
        print(member.filename, member.file_size, dependencies)
