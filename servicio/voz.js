// La voz de Claudia: el mismo texto a voz que usa en WhatsApp (lib/tts.js del repo), pasado a PCM 48 kHz mono de
// 16 bits, que es lo que Simple Voice Chat reproduce desde su cuerpo en el juego.
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { promises as fs } from "node:fs";
import { randomUUID } from "node:crypto";
import path from "node:path";
import { sintetizar } from "../../claudia/lib/tts.js";

const ejecutar = promisify(execFile);

export const VOZ = {
  CARPETA: path.join(import.meta.dirname, "..", "datos", "voz"),
  MAX_CARACTERES: 300, // lo mismo que entra en el globito
};

// Devuelve la ruta del .pcm listo para el plugin, o null si no se pudo (sin voz, Claudia igual habla por texto).
export async function hablar(texto) {
  const limpio = String(texto).slice(0, VOZ.MAX_CARACTERES).trim();
  if (!limpio) return null;
  await fs.mkdir(VOZ.CARPETA, { recursive: true });
  const id = randomUUID();
  const mp3 = path.join(VOZ.CARPETA, `${id}.mp3`);
  const pcm = path.join(VOZ.CARPETA, `${id}.pcm`);
  try {
    await fs.writeFile(mp3, await sintetizar(limpio, "es"));
    await ejecutar("ffmpeg", ["-v", "error", "-y", "-i", mp3, "-ac", "1", "-ar", "48000", "-f", "s16le", pcm], { timeout: 30_000 });
    return pcm;
  } catch (e) {
    console.error("[voz]", e.message);
    return null;
  } finally {
    await fs.rm(mp3, { force: true });
  }
}
