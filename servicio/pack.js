// Paquete de recursos con el tema que pidieron: así suena por el canal de música del juego, sin mods.
// El server se lo manda a cada jugador; lo descargan de http://<túnel TCP de playit>/<archivo>.zip, que sirve index.js.
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { createHash, randomUUID } from "node:crypto";
import { promises as fs } from "node:fs";
import path from "node:path";
import { MUSICA } from "./musica.js";

const ejecutar = promisify(execFile);

export const PACK = {
  CARPETA: path.join(import.meta.dirname, "..", "datos", "packs"),
  FORMATO: 88, // resource_major de la 26.2 (version.json del cliente)
  GUARDADOS: 3,
};

// Arma el .zip con el tema como claudia:tema y devuelve { id, archivo, sha1 }.
export async function armarPack(archivoMp3, titulo) {
  const id = randomUUID();
  const tmp = path.join(PACK.CARPETA, `armando-${id}`);
  const sonidos = path.join(tmp, "assets", "claudia", "sounds");
  await fs.mkdir(sonidos, { recursive: true });
  try {
    // Vorbis estéreo de calidad media: un tema de 3-4 minutos queda en unos 3 MB.
    await ejecutar("ffmpeg", ["-v", "error", "-y", "-i", path.join(MUSICA.CARPETA, `${archivoMp3}.mp3`), "-vn", "-ac", "2", "-c:a", "libvorbis", "-q:a", "2", path.join(sonidos, "tema.ogg")], { timeout: 120_000 });
    await fs.writeFile(path.join(tmp, "pack.mcmeta"), JSON.stringify({ pack: { description: `ClaudIA: ${titulo}`.slice(0, 80), min_format: PACK.FORMATO, max_format: PACK.FORMATO } }));
    await fs.writeFile(path.join(tmp, "assets", "claudia", "sounds.json"), JSON.stringify({ tema: { sounds: [{ name: "claudia:tema", stream: true }] } }));
    const archivo = `${id}.zip`;
    await ejecutar("zip", ["-q", "-r", "-X", path.join("..", archivo), "pack.mcmeta", "assets"], { cwd: tmp });
    const sha1 = createHash("sha1").update(await fs.readFile(path.join(PACK.CARPETA, archivo))).digest("hex");
    podar().catch(() => {});
    return { id, archivo, sha1 };
  } finally {
    await fs.rm(tmp, { recursive: true, force: true });
  }
}

async function podar() {
  const zips = (await fs.readdir(PACK.CARPETA)).filter((f) => f.endsWith(".zip"));
  const conFecha = await Promise.all(zips.map(async (f) => ({ f, t: (await fs.stat(path.join(PACK.CARPETA, f))).mtimeMs })));
  conFecha.sort((a, b) => b.t - a.t);
  for (const { f } of conFecha.slice(PACK.GUARDADOS)) await fs.rm(path.join(PACK.CARPETA, f), { force: true });
}
