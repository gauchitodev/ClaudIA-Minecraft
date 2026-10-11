import sys,os,struct,zlib
exec(open(sys.argv[1]).read().split("import zipfile")[0])
out=[]
d=sys.argv[2]
for f in os.listdir(d):
    if not f.endswith(".mca"): continue
    b=open(os.path.join(d,f),"rb").read()
    if len(b)<8192: continue
    for i in range(1024):
        off=int.from_bytes(b[i*4:i*4+3],"big"); 
        if not off: continue
        p=off*4096; ln=struct.unpack(">I",b[p:p+4])[0]; comp=b[p+4]
        data=b[p+5:p+4+ln]
        try:
            raw=zlib.decompress(data) if comp==2 else data
            r=R(raw); t=r.u(">b",1); r.st(); c=r.tag(t)
        except Exception as e: continue
        for be in c.get("block_entities",[]):
            if "command_block" in str(be.get("id","")) and be.get("Command"):
                out.append((be["x"],be["y"],be["z"],be.get("auto"),be["id"].split(":")[-1],be["Command"]))
for o in out: print(*o, sep="\t")
