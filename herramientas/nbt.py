import gzip,struct,sys,zlib
def rd(b):
    try: return gzip.decompress(b)
    except: 
        try: return zlib.decompress(b)
        except: return b
class R:
    def __init__(s,b): s.b=b; s.i=0
    def u(s,f,n): v=struct.unpack(f,s.b[s.i:s.i+n]); s.i+=n; return v[0]
    def st(s): l=s.u(">H",2); v=s.b[s.i:s.i+l].decode("utf8","replace"); s.i+=l; return v
    def tag(s,t):
        if t==1: return s.u(">b",1)
        if t==2: return s.u(">h",2)
        if t==3: return s.u(">i",4)
        if t==4: return s.u(">q",8)
        if t==5: return s.u(">f",4)
        if t==6: return s.u(">d",8)
        if t==7: n=s.u(">i",4); s.i+=n; return f"<bytes {n}>"
        if t==8: return s.st()
        if t==9:
            et=s.u(">b",1); n=s.u(">i",4); return [s.tag(et) for _ in range(n)]
        if t==10:
            d={}
            while True:
                tt=s.u(">b",1)
                if tt==0: return d
                k=s.st(); d[k]=s.tag(tt)
        if t==11: n=s.u(">i",4); v=[s.u(">i",4) for _ in range(n)]; return v
        if t==12: n=s.u(">i",4); s.i+=8*n; return f"<longs {n}>"
def load(b):
    r=R(rd(b)); t=r.u(">b",1); r.st(); return r.tag(t)
def walk(d,pre=""):
    if isinstance(d,dict):
        for k,v in d.items(): walk(v,pre+"."+k)
    else:
        if any(w in pre.lower() for w in ("spawn","pos","respawn","levelname","gamerule","dimension")): print(pre,"=",d if not isinstance(d,list) or len(d)<6 else d[:6])
import zipfile
z=zipfile.ZipFile(sys.argv[1])
for n in z.namelist():
    if n.endswith(("level.dat","6f4eb2d0-8ee8-4a43-92a9-9e67bc5794ea.dat","world_gen_settings.dat")) or "game_rules" in n:
        print("==",n)
        try: walk(load(z.read(n)))
        except Exception as e: print("err",e)
