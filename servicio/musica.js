// .play con yt-dlp, como el bot de WhatsApp (plugins/dl-youtube.js del repo): busca en YouTube y, si no sale, en
// SoundCloud. Baja el tema como mp3 a la carpeta "local" de CDisc, que lo pasa en el tocadiscos con local:<archivo>.
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { existsSync, promises as fs } from "node:fs";
import path from "node:path";
import { config } from "./config.js";

const ejecutar = promisify(execFile);

export const MUSICA = {
  CARPETA: path.join(config.carpetaServer, "plugins", "CDisc", "local"),
  MAX_SEGUNDOS: 600, // temas de hasta 10 minutos
  GUARDADOS: 40, // los más viejos se borran
  PROHIBIDO: ["anuel"], // herencia del grupo
};

// Las mismas cookies de YouTube que usa el bot de WhatsApp, si están (repo hermano ../claudia).
const COOKIES = path.join(import.meta.dirname, "..", "..", "claudia", "cookies.txt");
const cookiesArgs = () => (existsSync(COOKIES) ? ["--cookies", COOKIES] : []);

async function buscar(prefijo, query) {
  try {
    const { stdout } = await ejecutar("yt-dlp", [`${prefijo}:${query}`, ...cookiesArgs(), "--print", "%(title)s", "--print", "%(webpage_url)s", "--print", "%(duration)s", "--skip-download", "--no-warnings", "--ignore-errors"], { timeout: 120_000 });
    const l = stdout.trim().split("\n");
    const r = [];
    for (let i = 0; i + 2 < l.length; i += 3) r.push({ titulo: l[i], url: l[i + 1], duracion: Number(l[i + 2]) || 0 });
    return r.filter((c) => c.duracion > 0 && c.duracion <= MUSICA.MAX_SEGUNDOS);
  } catch {
    return [];
  }
}

const nombreArchivo = (titulo) =>
  titulo.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 60) || "tema";

async function bajar(candidato) {
  const nombre = nombreArchivo(candidato.titulo);
  const destino = path.join(MUSICA.CARPETA, `${nombre}.mp3`);
  if (existsSync(destino)) return nombre; // ya lo pidió alguien antes
  try {
    await ejecutar("yt-dlp", ["-f", "bestaudio/best", "--extract-audio", "--audio-format", "mp3", "--audio-quality", "5", ...cookiesArgs(), "--no-warnings", "--no-playlist", "-o", path.join(MUSICA.CARPETA, `${nombre}.%(ext)s`), candidato.url], { timeout: 5 * 60_000, maxBuffer: 10 * 1024 * 1024 });
  } catch (e) {
    console.error(`[musica] falló "${candidato.titulo}": ${String(e.stderr || e.message).slice(0, 200)}`);
  }
  return existsSync(destino) ? nombre : null;
}

async function podar() {
  const archivos = (await fs.readdir(MUSICA.CARPETA)).filter((f) => f.endsWith(".mp3"));
  if (archivos.length <= MUSICA.GUARDADOS) return;
  const conFecha = await Promise.all(archivos.map(async (f) => ({ f, t: (await fs.stat(path.join(MUSICA.CARPETA, f))).mtimeMs })));
  conFecha.sort((a, b) => a.t - b.t);
  for (const { f } of conFecha.slice(0, archivos.length - MUSICA.GUARDADOS)) await fs.rm(path.join(MUSICA.CARPETA, f), { force: true });
}

// Devuelve { ok: true, archivo, titulo } o { ok: false, motivo }.
export async function conseguirTema(query) {
  if (MUSICA.PROHIBIDO.some((p) => query.toLowerCase().includes(p))) return { ok: false, motivo: "prohibido" };
  await fs.mkdir(MUSICA.CARPETA, { recursive: true });
  const esLink = /^https?:\/\/\S+$/i.test(query);
  // SoundCloud se busca solo si nada de YouTube se pudo bajar.
  const tandas = esLink ? [async () => [{ titulo: query.replace(/^https?:\/\//, ""), url: query }]] : [() => buscar("ytsearch3", query), () => buscar("scsearch5", query)];
  for (const tanda of tandas) {
    for (const c of await tanda()) {
      const archivo = await bajar(c);
      if (archivo) {
        podar().catch(() => {});
        return { ok: true, archivo, titulo: c.titulo };
      }
    }
  }
  return { ok: false, motivo: "no-salio" };
}
