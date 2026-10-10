// UruCoins del server de Minecraft. Base propia (no la del bot de WhatsApp): datos/claudia-mc.db.
import { DatabaseSync } from "node:sqlite";
import { mkdirSync } from "node:fs";
import path from "node:path";

export const COINS = {
  DIARIO: 50, // .diario, una vez por día
  TRIVIA: 30, // por ganar una trivia
  MAX_REGALO: 5000,
};

const RUTA = path.join(import.meta.dirname, "..", "datos", "claudia-mc.db");
mkdirSync(path.dirname(RUTA), { recursive: true });
const db = new DatabaseSync(RUTA);
db.exec(`CREATE TABLE IF NOT EXISTS jugadores (
  nombre TEXT PRIMARY KEY COLLATE NOCASE,
  coins INTEGER NOT NULL DEFAULT 0,
  ultimo_diario TEXT,
  racha INTEGER NOT NULL DEFAULT 0,
  trivias INTEGER NOT NULL DEFAULT 0,
  memoria TEXT
)`);

const asegurar = db.prepare("INSERT OR IGNORE INTO jugadores (nombre) VALUES (?)");
const leer = db.prepare("SELECT * FROM jugadores WHERE nombre = ?");

export function jugador(nombre) {
  asegurar.run(nombre);
  return leer.get(nombre);
}

export function sumar(nombre, cantidad) {
  jugador(nombre);
  db.prepare("UPDATE jugadores SET coins = coins + ? WHERE nombre = ?").run(cantidad, nombre);
  return jugador(nombre).coins;
}

export function top(n = 5) {
  return db.prepare("SELECT nombre, coins FROM jugadores ORDER BY coins DESC LIMIT ?").all(n);
}

const hoy = () => new Date().toLocaleDateString("sv-SE", { timeZone: "America/Montevideo" });
const ayer = () => new Date(Date.now() - 864e5).toLocaleDateString("sv-SE", { timeZone: "America/Montevideo" });

// Devuelve { ok, cobrado, racha } o { ok: false } si ya cobró hoy.
export function cobrarDiario(nombre) {
  const j = jugador(nombre);
  if (j.ultimo_diario === hoy()) return { ok: false };
  const racha = j.ultimo_diario === ayer() ? j.racha + 1 : 1;
  const cobrado = COINS.DIARIO + Math.min(racha - 1, 6) * 10;
  db.prepare("UPDATE jugadores SET coins = coins + ?, ultimo_diario = ?, racha = ? WHERE nombre = ?").run(cobrado, hoy(), racha, nombre);
  return { ok: true, cobrado, racha };
}

export function regalar(de, para, cantidad) {
  if (!(cantidad > 0) || cantidad > COINS.MAX_REGALO) return { ok: false, motivo: "cantidad" };
  if (jugador(de).coins < cantidad) return { ok: false, motivo: "saldo" };
  db.exec("BEGIN");
  sumar(de, -cantidad);
  sumar(para, cantidad);
  db.exec("COMMIT");
  return { ok: true };
}

export function ganoTrivia(nombre) {
  jugador(nombre);
  db.prepare("UPDATE jugadores SET coins = coins + ?, trivias = trivias + 1 WHERE nombre = ?").run(COINS.TRIVIA, nombre);
  return jugador(nombre).coins;
}

// Memoria corta de cada jugador: datos sueltos que Claudia recuerda entre charlas (máx. 6).
export function recordar(nombre, dato) {
  const j = jugador(nombre);
  const lista = JSON.parse(j.memoria || "[]").filter((d) => d !== dato);
  lista.push(dato);
  db.prepare("UPDATE jugadores SET memoria = ? WHERE nombre = ?").run(JSON.stringify(lista.slice(-6)), nombre);
}

export const memoriaDe = (nombre) => JSON.parse(jugador(nombre).memoria || "[]");
