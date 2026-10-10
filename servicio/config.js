// Lee config.json (copiá config.example.json y completalo). Lo que falte toma el valor por defecto.
import { readFileSync } from "node:fs";
import path from "node:path";
import os from "node:os";

const leido = JSON.parse(readFileSync(path.join(import.meta.dirname, "..", "config.json"), "utf8"));

export const config = {
  geminiApiKey: "",
  groqApiKey: "",
  openrouterApiKey: "",
  nvidiaApiKey: "",
  cerebrasApiKey: "",
  // Carpeta del server de Paper: la música se baja a <carpetaServer>/plugins/CDisc/local.
  carpetaServer: path.join(os.homedir(), "mc-server"),
  // Dirección pública desde la que los jugadores bajan los paquetes de música (túnel TCP o puerto abierto).
  // Vacía: .play da un disco de CDisc, que solo se escucha con el mod Simple Voice Chat.
  packUrlBase: "",
  puertoPacks: 34964,
  // Para que Claudia sepa dónde está: quién es el dueño y de qué va el mapa.
  dueno: "el dueño del server",
  duenoEnJuego: "",
  mapa: "",
  ...leido,
};

for (const k of ["geminiApiKey", "groqApiKey", "openrouterApiKey", "nvidiaApiKey", "cerebrasApiKey"]) globalThis[k] = config[k];
