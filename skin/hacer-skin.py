# Skin de ClaudIA para Minecraft (64x64): la cara de la foto de perfil (melena verde estilo Matrix, ojos verdes que
# brillan) sobre el cuerpo de Bender (gris metálico, brazos y piernas con anillos, puertita en el pecho).
# Sin dependencias: escribe el PNG a mano. Uso: python3 hacer-skin.py claudia.png
import random
import struct
import sys
import zlib

random.seed(7)
W = H = 64
px = [[(0, 0, 0, 0)] * W for _ in range(H)]


def put(x, y, c):
    px[y][x] = c if len(c) == 4 else (*c, 255)


def ruido(c, n=10):
    d = random.randint(-n, n)
    return tuple(max(0, min(255, v + d)) for v in c[:3])


def rect(x0, y0, w, h, f):
    for y in range(h):
        for x in range(w):
            put(x0 + x, y0 + y, f(x, y, w, h))


# ---------- colores ----------
PELO_OSC, PELO, PELO_BRILLO = (18, 90, 45), (40, 170, 80), (120, 255, 150)
PIEL, PIEL_SOMBRA = (165, 215, 180), (125, 180, 140)
OJO, OJO_BRILLO = (20, 140, 60), (90, 255, 140)
METAL, METAL_OSC, METAL_CLARO = (150, 158, 164), (100, 108, 114), (195, 202, 206)
PIE = (90, 96, 100)


def pelo(x, y, w, h):
    # mechones verticales: unos más brillantes que otros, como el código que cae
    if (x * 7 + y * 3) % 11 == 0:
        return PELO_BRILLO
    return ruido(PELO if x % 2 else PELO_OSC, 8)


def metal(anillos=False, mano=False, pie=False):
    def f(x, y, w, h):
        if pie and y >= h - 2:
            return ruido(PIE, 4)
        if mano and y >= h - 3:
            return ruido(METAL_CLARO, 5)
        if anillos and y % 3 == 2:
            return ruido(METAL_OSC, 4)
        base = METAL_CLARO if x == 0 else METAL
        return ruido(base, 6)
    return f


# ---------- cabeza (Claudia) ----------
rect(8, 0, 8, 8, pelo)      # arriba
rect(16, 0, 8, 8, lambda x, y, w, h: ruido(PIEL_SOMBRA, 5))  # abajo (cuello)
rect(0, 8, 8, 8, pelo)      # costado derecho
rect(16, 8, 8, 8, pelo)     # costado izquierdo
rect(24, 8, 8, 8, pelo)     # nuca


def cara(x, y, w, h):
    # melena tipo bob como en la foto: flequillo de costado (más largo a la izquierda) y pelo hasta la mandíbula
    if y <= 1 or (y == 2 and x not in (4, 5, 6)) or (x == 0 and y <= 6) or (x == 7 and y <= 5):
        return pelo(x, y, w, h)
    if y == 3 and x in (1, 2, 5, 6):
        return ruido(PELO_OSC, 3)  # cejas
    if y == 4 and x in (1, 6):
        return (225, 255, 235)  # blanco de los ojos
    if y == 4 and x in (2, 5):
        return OJO_BRILLO  # iris verde que brilla
    if y == 6 and x in (3, 4):
        return (95, 150, 110)  # boca
    if y == 5 and x in (1, 6):
        return (60, 230, 120)  # circuitos en las mejillas
    return ruido(PIEL if 2 <= x <= 5 else PIEL_SOMBRA, 4)


rect(8, 8, 8, 8, cara)
# capa de afuera de la cabeza: algunos mechones sueltos que brillan sobre la melena
for (x, y) in [(41, 8), (46, 8), (40, 10), (47, 11), (42, 9), (45, 9)]:
    put(x, y, (*PELO_BRILLO, 255))

# ---------- cuerpo (Bender) ----------
rect(20, 16, 8, 4, metal())          # arriba
rect(28, 16, 8, 4, metal())          # abajo
rect(16, 20, 4, 12, metal())         # costado
rect(28, 20, 4, 12, metal())         # costado
rect(32, 20, 8, 12, metal())         # espalda


def pecho(x, y, w, h):
    if 1 <= x <= 6 and 3 <= y <= 9:
        if x in (1, 6) or y in (3, 9):
            return ruido(METAL_OSC, 3)  # marco de la puertita
        if x == 5 and y == 6:
            return (60, 60, 60)  # manija
        return ruido(METAL_CLARO, 4)
    return metal()(x, y, w, h)


rect(20, 20, 8, 12, pecho)

# ---------- brazos y piernas ----------
for (x0, y0) in [(44, 16), (48, 16), (36, 48), (40, 48)]:  # tapas de los brazos
    rect(x0, y0, 4, 4, metal())
for x0 in (40, 44, 48, 52):           # brazo derecho
    rect(x0, 20, 4, 12, metal(anillos=True, mano=True))
for x0 in (32, 36, 40, 44):           # brazo izquierdo
    rect(x0, 52, 4, 12, metal(anillos=True, mano=True))
for (x0, y0) in [(4, 16), (8, 16), (20, 48), (24, 48)]:   # tapas de las piernas
    rect(x0, y0, 4, 4, metal())
for x0 in (0, 4, 8, 12):              # pierna derecha
    rect(x0, 20, 4, 12, metal(anillos=True, pie=True))
for x0 in (16, 20, 24, 28):           # pierna izquierda
    rect(x0, 52, 4, 12, metal(anillos=True, pie=True))


# ---------- PNG ----------
def png(ruta):
    filas = b"".join(b"\x00" + bytes(v for p in fila for v in p) for fila in px)

    def trozo(tipo, datos):
        return struct.pack(">I", len(datos)) + tipo + datos + struct.pack(">I", zlib.crc32(tipo + datos) & 0xFFFFFFFF)

    with open(ruta, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(trozo(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0)))
        f.write(trozo(b"IDAT", zlib.compress(filas, 9)))
        f.write(trozo(b"IEND", b""))


png(sys.argv[1] if len(sys.argv) > 1 else "claudia.png")
