# Actualiza predicados de entidad al formato de la 26.2: las claves pasan a llevar "minecraft:" y type_specific se
# vuelve una clave propia. Toca solo los objetos que son predicados de entidad.
import json, sys, os
CAMPOS = {"type": "minecraft:entity_type", "nbt": "minecraft:nbt", "location": "minecraft:location",
          "stepping_on": "minecraft:stepping_on", "movement_affected_by": "minecraft:movement_affected_by",
          "distance": "minecraft:distance", "movement": "minecraft:movement", "flags": "minecraft:flags",
          "equipment": "minecraft:equipment", "effects": "minecraft:effects", "team": "minecraft:team",
          "vehicle": "minecraft:vehicle", "passenger": "minecraft:passenger", "targeted_entity": "minecraft:targeted_entity",
          "periodic_tick": "minecraft:periodic_tick", "slots": "minecraft:slots", "components": "minecraft:components",
          "predicates": "minecraft:predicates"}
ANIDADOS = {"vehicle", "passenger", "targeted_entity"}
LLAVES_ENTIDAD = {"entity", "player", "source_entity", "direct_entity", "projectile", "shooter", "victim", "parent",
                  "partner", "child", "zombie", "villager", "lightning", "bystander", "killing_blow"}
cambios = 0

def entidad(o):
    global cambios
    if not isinstance(o, dict): return o
    n = {}
    for k, v in o.items():
        if k == "type_specific" and isinstance(v, dict) and "type" in v:
            t = v.pop("type"); n[t if ":" in t else "minecraft:" + t] = v; cambios += 1; continue
        nk = CAMPOS.get(k, k)
        if nk != k: cambios += 1
        n[nk] = entidad(v) if k in ANIDADOS else v
    return n

def recorrer(o):
    if isinstance(o, dict):
        if o.get("condition") in ("minecraft:entity_properties", "entity_properties") and isinstance(o.get("predicate"), dict):
            o["predicate"] = entidad(o["predicate"])
        for k in list(o):
            if k in LLAVES_ENTIDAD and isinstance(o[k], dict): o[k] = entidad(o[k])
            else: recorrer(o[k])
    elif isinstance(o, list):
        for x in o: recorrer(x)

for raiz, _, archivos in os.walk(sys.argv[1]):
    for a in archivos:
        if not a.endswith(".json") or not any(p in raiz for p in ("/advancement", "/predicate")): continue
        p = os.path.join(raiz, a); antes = cambios
        d = json.load(open(p, encoding="utf8")); recorrer(d)
        if cambios != antes: json.dump(d, open(p, "w", encoding="utf8"), indent=4, ensure_ascii=False)
print("claves actualizadas:", cambios)
