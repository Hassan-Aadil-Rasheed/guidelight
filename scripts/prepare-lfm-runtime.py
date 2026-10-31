from pathlib import Path
import struct,shutil,hashlib,json
root=Path(__file__).resolve().parents[1]; src=root/'.tools/lfm/llama-b11960'; target=root/'app/src/main/jniLibs/arm64-v8a'
def deps(p):
 data=p.read_bytes(); phoff=struct.unpack_from('<Q',data,32)[0]; phsize,phnum=struct.unpack_from('<HH',data,54)
 segments=[struct.unpack_from('<IIQQQQQQ',data,phoff+i*phsize) for i in range(phnum)]
 dyn=next(p for p in segments if p[0]==2); entries=[struct.unpack_from('<QQ',data,i) for i in range(dyn[2],dyn[2]+dyn[5],16)]
 address=next(v for k,v in entries if k==5); load=next(p for p in segments if p[0]==1 and p[3]<=address<p[3]+p[5]); table=load[2]+address-load[3]
 return [data[table+v:data.index(0,table+v)].decode() for k,v in entries if k==1]
selected={'llama-server'}; todo=['llama-server']
while todo:
 name=todo.pop()
 for dep in deps(src/name):
  if (src/dep).is_file() and dep not in selected: selected.add(dep);todo.append(dep)
selected.update(p.name for p in src.glob('libggml-cpu-*.so'))
manifest=[]
for name in sorted(selected):
 destination='libguidelight_lfm_server.so' if name=='llama-server' else name
 data=(src/name).read_bytes(); path=target/destination
 if path.exists() and path.read_bytes()!=data and not name.startswith(('libggml','libllama','libmtmd')): raise RuntimeError('Existing native library conflict '+name)
 shutil.copyfile(src/name,path)
 manifest.append({'source':name,'destination':destination,'sha256':hashlib.sha256(data).hexdigest(),'bytes':len(data),'dependencies':deps(src/name)})
assets=root/'app/src/main/assets/local-ai';assets.mkdir(parents=True,exist_ok=True)
shutil.copyfile(src/'LICENSE',assets/'llama-LICENSE.txt')
(assets/'runtime-provenance.json').write_text(json.dumps({'release':'b11960','archive_sha256':'77d9f19fad41d6d8efbc29bcc1c1ab60a605018564cfc32203892eb1973ba4b9','libraries':manifest},indent=2))
print('Packaged',len(manifest),'runtime files,',sum(x['bytes'] for x in manifest),'bytes')
for item in manifest:print(item['destination'],item['dependencies'])
