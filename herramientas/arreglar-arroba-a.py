import sys,re,csv
# Genera comandos de consola que reescriben los bloques de comandos de un mapa para que no afecten a todo el server.
dim, modo, tsv = sys.argv[1], sys.argv[2], sys.argv[3]
def nuevo(c):
    if modo == "individual":
        return re.sub(r"@a\b", "@p", c)
    # cooperativo: todos, pero solo los de este mundo (distance obliga a la misma dimensión)
    c = re.sub(r"@a\[", "@a[distance=..3000,", c)
    return re.sub(r"@a(?!\[)", "@a[distance=..3000]", c)
chunks=set(); cambios=[]
for fila in open(tsv, encoding="utf8"):
    x,y,z,auto,tipo,cmd = fila.rstrip("\n").split("\t",5)
    if "@a" not in cmd: continue
    n = nuevo(cmd)
    if n == cmd: continue
    chunks.add((int(x)>>4, int(z)>>4))
    snbt = "'" + n.replace("\\","\\\\").replace("'","\\'") + "'"
    cambios.append(f"execute in {dim} run data modify block {x} {y} {z} Command set value {snbt}")
for cx,cz in sorted(chunks): print(f"execute in {dim} run forceload add {cx*16} {cz*16}")
for c in cambios: print(c)
for cx,cz in sorted(chunks): print(f"execute in {dim} run forceload remove {cx*16} {cz*16}")
print(f"#CAMBIOS {len(cambios)}", file=sys.stderr)
