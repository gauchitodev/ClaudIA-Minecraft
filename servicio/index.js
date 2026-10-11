// ClaudIA en Minecraft: el "cerebro". El plugin ClaudiaMC le manda lo que pasa en el chat y ejecuta lo que esto devuelve.
// Reusa del repo de ClaudIA (~/claudia) la personalidad, la IA con sus respaldos, la trivia, el clima y la RAE.
import http from "node:http";
import { config } from "./config.js";
import path from "node:path";
import { preguntarGemini } from "../../claudia/lib/gemini.js";
import { preguntarRespaldo } from "../../claudia/lib/respaldo-ia.js";
import { BANCO } from "../../claudia/lib/trivia-banco.js";
import { textoClimaDe } from "../../claudia/lib/clima.js";
import { definir } from "../../claudia/lib/rae.js";
import * as eco from "./economia.js";
import { conseguirTema } from "./musica.js";
import { hablar } from "./voz.js";
import { armarPack, PACK } from "./pack.js";
import { createReadStream } from "node:fs";

const PUERTO = 8791;
const PUERTO_PACKS = config.puertoPacks; // el puerto local al que apunta el túnel de los paquetes

const AJUSTES = {
  COOLDOWN_IA_MS: 6000, // como mucho una respuesta de IA cada 6 s en todo el server
  LINEAS_CONTEXTO: 20,
  LARGO_MAX: 320, // el chat de Minecraft es chico
  TRIVIA_MS: 30000,
};

// Lo que Claudia tiene que hacer y el plugin todavía no buscó. El plugin lo pide cada 2 s y con cada evento.
const pendientes = [];
const decir = (texto) => pendientes.push({ tipo: "decir", texto: paraMinecraft(texto) });
const privado = (jugador, texto) => pendientes.push({ tipo: "privado", jugador, texto: paraMinecraft(texto) });

// El texto del repo viene con formato de WhatsApp (*negrita*, _cursiva_) y emojis que Minecraft no dibuja.
function paraMinecraft(texto) {
  const limpio = String(texto)
    .replace(/[*_~`]/g, "")
    .replace(/[\u{1F000}-\u{1FAFF}\u{2600}-\u{27BF}\u{FE0F}\u{200D}]/gu, "")
    .replace(/[ \t]+/g, " ")
    .trim();
  return limpio.length > AJUSTES.LARGO_MAX ? `${limpio.slice(0, AJUSTES.LARGO_MAX - 1)}…` : limpio;
}

// ---------- Charla con IA ----------
const historial = [];
let ultimaIA = 0;
let pensando = false;

const CONTEXTO_MC = (jugadores) => `Ahora NO estás en WhatsApp: estás en el chat de un server de Minecraft de ${config.dueno}${config.duenoEnJuego ? ` (en el juego es ${config.duenoEnJuego})` : ""}, jugando con sus amigos.${config.mapa ? ` El mapa es ${config.mapa}.` : ""} Conectados: ${jugadores.join(", ") || "nadie más"}.
Reglas extra para este chat: respondé MUY corto (una frase, dos como mucho), sin markdown, sin asteriscos, sin emojis, sin links. Tenés cuerpo en el mundo: andás al lado de quien te habla, y lo que decís se escucha con tu voz y aparece en un globito arriba tuyo. Tus comandos acá son: .play <tema>, .trivia, .clima <lugar>, .rae <palabra>, .saldo, .diario, .top, .regalar <jugador> <monto>, .recordá <dato>, .menu. No inventes otros.`;

async function charlar(jugador, texto, online) {
  if (pensando || Date.now() - ultimaIA < AJUSTES.COOLDOWN_IA_MS) return;
  pensando = true;
  ultimaIA = Date.now();
  pendientes.push({ tipo: "atencion", jugador }); // su cuerpo se acerca mientras piensa
  try {
    const memoria = eco.memoriaDe(jugador);
    const prompt = [
      CONTEXTO_MC(online),
      memoria.length ? `Lo que sabés de ${jugador}: ${memoria.join("; ")}.` : "",
      `Últimos mensajes del chat:\n${historial.slice(-AJUSTES.LINEAS_CONTEXTO).join("\n")}`,
      `Respondele a ${jugador}, que te dijo: "${texto}"`,
    ].filter(Boolean).join("\n\n");
    let r = await preguntarGemini(prompt);
    if (!r.ok) r = await preguntarRespaldo(prompt);
    if (r.ok) {
      const dicho = paraMinecraft(r.texto);
      decir(dicho);
      pendientes.push({ tipo: "globo", texto: dicho });
      historial.push(`ClaudIA: ${dicho}`);
      // La voz llega unos segundos después del texto: no se espera por ella.
      hablar(dicho).then((archivo) => archivo && pendientes.push({ tipo: "voz", archivo }));
    } else {
      console.error("[ia] no respondió ningún modelo");
    }
  } finally {
    pensando = false;
  }
}

// ---------- Trivia ----------
let trivia = null; // { correcta, opciones, intentaron: Set, timer }

function empezarTrivia() {
  if (trivia) return decir("Ya hay una trivia andando, contestá esa primero.");
  const p = BANCO[Math.floor(Math.random() * BANCO.length)];
  const letras = ["a", "b", "c", "d"];
  trivia = {
    correcta: p.correcta,
    texto: p.opciones[letras.indexOf(p.correcta)],
    intentaron: new Set(),
    timer: setTimeout(() => {
      decir(`Se terminó el tiempo. Era la ${trivia.correcta}) ${trivia.texto}.`);
      trivia = null;
    }, AJUSTES.TRIVIA_MS),
  };
  decir(`Trivia (${AJUSTES.TRIVIA_MS / 1000} s, ${eco.COINS.TRIVIA} UruCoins): ${p.pregunta}`);
  decir(p.opciones.map((o, i) => `${letras[i]}) ${o}`).join("   "));
  decir("Contestá solo con la letra. Un intento por persona.");
}

function responderTrivia(jugador, letra) {
  if (trivia.intentaron.has(jugador)) return;
  trivia.intentaron.add(jugador);
  if (letra !== trivia.correcta) return;
  clearTimeout(trivia.timer);
  const saldo = eco.ganoTrivia(jugador);
  decir(`¡Bien ahí ${jugador}! Era la ${trivia.correcta}) ${trivia.texto}. +${eco.COINS.TRIVIA} UruCoins (tenés ${saldo}).`);
  trivia = null;
}

// ---------- Comandos ----------
const COMANDOS = {
  menu: (j) => {
    privado(j, "Comandos de Claudia: .play <tema> (suena para todos) · .parar · .trivia · .clima <lugar> · .rae <palabra> · .saldo · .diario · .top · .regalar <jugador> <monto> · .recordá <dato>. Y si me nombrás, te contesto.");
  },
  play: (j, arg) => {
    if (!arg) return privado(j, "Decime qué tema: .play nombre del tema (o un link de YouTube)");
    privado(j, `Buscando «${arg}»...`);
    // Sin await: bajar un tema tarda, y el resultado llega por pendientes.
    conseguirTema(arg).then(async (r) => {
      if (!r.ok) return privado(j, r.motivo === "prohibido" ? "Eso no lo pongo ni loca." : `No pude conseguir «${arg}». Probá con otro nombre o pasame el link.`);
      if (!config.packUrlBase) {
        // Sin túnel para los paquetes queda el disco de CDisc (solo lo escuchan los que tienen el mod de voz).
        pendientes.push({ tipo: "disco", jugador: j, busqueda: `local:${r.archivo}.mp3` });
        return decir(`${j} puso «${r.titulo}». Tiene el disco en la mano: al tocadiscos.`);
      }
      try {
        const p = await armarPack(r.archivo, r.titulo);
        pendientes.push({ tipo: "musica", id: p.id, url: `${config.packUrlBase}/${p.archivo}`, sha1: p.sha1 });
        decir(`${j} puso «${r.titulo}». Aceptá el paquete de recursos y suena.`);
      } catch (e) {
        console.error("[pack]", e.message);
        privado(j, "Bajé el tema pero no pude armar el paquete. Avisale al dueño del server.");
      }
    });
  },
  parar: () => {
    pendientes.push({ tipo: "parar" });
    decir("Listo, música apagada.");
  },
  trivia: () => empezarTrivia(),
  clima: async (j, arg) => decir(await textoClimaDe(arg)),
  rae: async (j, arg) => {
    if (!arg) return privado(j, "Decime la palabra: .rae palabra");
    const r = await definir(arg);
    decir(r ? r.split("\n").slice(0, 4).join(" ") : `No encontré «${arg}» en la RAE.`);
  },
  saldo: (j) => privado(j, `Tenés ${eco.jugador(j).coins} UruCoins.`),
  diario: (j) => {
    const r = eco.cobrarDiario(j);
    if (!r.ok) return privado(j, "Ya cobraste hoy. Volvé mañana.");
    decir(`${j} cobró su diario: +${r.cobrado} UruCoins${r.racha > 1 ? ` (racha de ${r.racha} días)` : ""}.`);
  },
  top: () => {
    const t = eco.top(5).filter((x) => x.coins > 0);
    decir(t.length ? `Top UruCoins: ${t.map((x, i) => `${i + 1}. ${x.nombre} (${x.coins})`).join("  ")}` : "Nadie tiene UruCoins todavía. Arranquen con .diario");
  },
  regalar: (j, arg, online) => {
    const [para, monto] = arg.split(/\s+/);
    const n = parseInt(monto, 10);
    if (!para || !n) return privado(j, "Así: .regalar jugador monto");
    const destino = online.find((o) => o.toLowerCase() === para.toLowerCase());
    if (!destino) return privado(j, `No veo a ${para} conectado.`);
    if (destino.toLowerCase() === j.toLowerCase()) return privado(j, "¿A vos mismo? Nah.");
    const r = eco.regalar(j, destino, n);
    if (!r.ok) return privado(j, r.motivo === "saldo" ? "No te alcanza." : `Entre 1 y ${eco.COINS.MAX_REGALO}.`);
    decir(`${j} le regaló ${n} UruCoins a ${destino}.`);
  },
  "recordá": (j, arg) => {
    if (!arg) return privado(j, "¿Qué querés que me acuerde? .recordá dato");
    eco.recordar(j, arg.slice(0, 120));
    privado(j, "Listo, me lo anoté.");
  },
};
COMANDOS.recorda = COMANDOS["recordá"];
COMANDOS.ayuda = COMANDOS.menu;
COMANDOS.stop = COMANDOS.parar;

// ---------- Eventos del plugin ----------
async function evento(e) {
  const online = Array.isArray(e.online) ? e.online : [];
  if (e.tipo === "entra") {
    privado(e.jugador, `Hola ${e.jugador}, soy Claudia. Escribí .menu para ver lo que sé hacer.`);
    return;
  }
  if (e.tipo !== "chat") return;
  const texto = String(e.texto || "").trim();
  if (!texto) return;
  historial.push(`${e.jugador}: ${texto}`);
  if (historial.length > 50) historial.splice(0, historial.length - 50);

  if (trivia && /^[a-d]$/i.test(texto)) return responderTrivia(e.jugador, texto.toLowerCase());

  const cmd = texto.match(/^\.(\S+)\s*(.*)$/s);
  if (cmd) {
    const fn = COMANDOS[cmd[1].toLowerCase()];
    if (fn) await fn(e.jugador, cmd[2].trim(), online);
    return;
  }
  if (/\bclaud[i1]a\b/i.test(texto)) charlar(e.jugador, texto, online); // sin await: la respuesta llega por pendientes
}

http.createServer(async (req, res) => {
  try {
    if (req.method === "POST" && req.url === "/evento") {
      let cuerpo = "";
      for await (const parte of req) cuerpo += parte;
      await evento(JSON.parse(cuerpo));
    } else if (!(req.method === "GET" && req.url === "/pendientes")) {
      res.writeHead(404).end();
      return;
    }
  } catch (err) {
    console.error("[evento]", err.message);
  }
  res.writeHead(200, { "Content-Type": "application/json" });
  res.end(JSON.stringify({ acciones: pendientes.splice(0) }));
}).listen(PUERTO, "127.0.0.1", () => console.log(`[claudia-mc] escuchando en 127.0.0.1:${PUERTO}`));

// Los paquetes de música, para que los bajen los jugadores. Solo archivos <uuid>.zip de datos/packs.
http.createServer((req, res) => {
  // /<uuid>.zip: los temas de .play. /fijo/<nombre>.zip: el paquete propio del mapa (datos/packs-fijos), que va en
  // resource-pack de server.properties.
  const m = req.url.match(/^\/([0-9a-f-]{36}\.zip)$/);
  const f = req.url.match(/^\/fijo\/([a-z0-9-]+\.zip)$/);
  if (req.method !== "GET" || !(m || f)) return res.writeHead(404).end();
  const ruta = m ? path.join(PACK.CARPETA, m[1]) : path.join(PACK.CARPETA, "..", "packs-fijos", f[1]);
  const flujo = createReadStream(ruta);
  flujo.on("open", () => res.writeHead(200, { "Content-Type": "application/zip" }));
  flujo.on("error", () => res.writeHead(404).end());
  flujo.pipe(res);
}).listen(PUERTO_PACKS, "127.0.0.1", () => console.log(`[claudia-mc] paquetes en 127.0.0.1:${PUERTO_PACKS}`));
