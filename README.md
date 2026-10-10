# ClaudIA para Minecraft 🇺🇾

[ClaudIA](https://github.com/gauchitodev/ClaudIA), el bot de WhatsApp del grupo, metida en el chat de un server de Minecraft Java (Paper). Habla con la misma personalidad, pone música de YouTube que suena para todos sin instalar mods, y trae trivia y UruCoins propias del server.

## ¿Qué hace?

- **Charla con IA**: si la nombran en el chat ("claudia, ¿qué onda?"), contesta con la personalidad de ClaudIA: Gemini con los mismos respaldos (Groq, OpenRouter, NVIDIA, Cerebras). Sabe quién está conectado, lee los últimos mensajes y se acuerda de lo que cada uno le pide con `.recordá`.
- **Música para todos, sin mods**: `.play <tema o link>` busca con yt-dlp en YouTube (y en SoundCloud si no sale), arma un paquete de recursos con ese tema y el server se lo manda a los jugadores. Al aceptarlo, suena por el canal de discos del juego. `.parar` la corta.
- **Trivia**: `.trivia` saca una pregunta del banco de ClaudIA (292 preguntas); el primero que acierta gana UruCoins.
- **UruCoins del server**: `.diario` (con racha), `.saldo`, `.top`, `.regalar <jugador> <monto>`. Es una economía aparte de la de WhatsApp.
- **Herramientas**: `.clima <lugar>` y `.rae <palabra>`, las mismas de ClaudIA.
- **Punto de aparición exacto**: los que entran por primera vez o mueren sin cama aparecen justo en el `/setworldspawn`. Paper corrige la altura con el mapa de alturas, y en los mapas de aventura eso a veces los tira al vacío.
- `.menu` muestra todo.

## Cómo funciona

```
 chat de Minecraft ──► plugin ClaudiaMC (Java, en el server) ──HTTP──► servicio (Node.js, 127.0.0.1:8791)
                                ▲                                            │
                                └──── acciones: hablar, susurrar, música ◄───┘
```

- **`plugin/`**: plugin de Paper. Manda cada mensaje del chat al servicio y ejecuta lo que el servicio le devuelve. No tiene lógica propia.
- **`servicio/`**: el "cerebro", en Node.js. **No copia el código de ClaudIA: lo importa** del repo [gauchitodev/ClaudIA](https://github.com/gauchitodev/ClaudIA) clonado al lado (`../claudia`). Usa de ahí la personalidad, la IA con sus respaldos, el banco de trivia, el clima y la RAE. Lo que se mejora en ClaudIA llega solo acá.
- Los paquetes de música los sirve el mismo servicio en `puertoPacks`. Los jugadores los bajan de `packUrlBase`, que tiene que ser una dirección pública: un túnel TCP de [playit.gg](https://playit.gg) o un puerto abierto en el router.

## Instalación

### Qué hace falta

- Un server de **Paper** para la versión que juegan (probado con la 26.2), ya arrancado una vez.
- **Node.js 22 o más nuevo**, **yt-dlp** y **ffmpeg**.
- Un **JDK 25** para compilar el plugin (sirve uno portátil, por ejemplo Temurin).
- El repo **ClaudIA** clonado al lado de este, con sus dependencias (`npm ci`):

```
~/claudia      ← git clone https://github.com/gauchitodev/ClaudIA.git claudia
~/claudia-mc   ← este repo
```

- Una API key de **Gemini** (gratis en [Google AI Studio](https://aistudio.google.com/apikey)). Conviene que sea otra que la del bot de WhatsApp, así no se comen la cuota entre los dos.
- Opcional: [CDisc Music](https://modrinth.com/plugin/cdisc-music) y [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) en el server. Si `packUrlBase` está vacío, `.play` da un disco de CDisc en vez del paquete (solo lo escuchan los que tienen el mod de voz).

### Pasos

```sh
git clone https://github.com/gauchitodev/ClaudIA-Minecraft.git claudia-mc
cd claudia-mc
cp config.example.json config.json   # y completalo
```

En `config.json`:

| Clave | Qué es |
|---|---|
| `geminiApiKey` | La key de Gemini. Las otras keys son los respaldos, opcionales. |
| `carpetaServer` | Carpeta del server de Paper. |
| `packUrlBase` | Dirección pública de los paquetes de música, por ejemplo `http://algo.tun.ply.gg:34964`. |
| `puertoPacks` | Puerto local donde el servicio sirve los paquetes (al que apunta el túnel). |
| `dueno`, `duenoEnJuego`, `mapa` | Para que Claudia sepa de quién es el server y de qué va el mapa. |

Compilar el plugin e instalarlo:

```sh
./plugin/compilar.sh /ruta/al/server
cp plugin/build/ClaudiaMC.jar /ruta/al/server/plugins/
```

Arrancar el servicio (por ejemplo en un `tmux` aparte) y después reiniciar el server:

```sh
node servicio/index.js
```

Si el servicio está apagado el server anda igual: Claudia simplemente no contesta.

## Notas

- **Bedrock** (consolas y celulares, vía Geyser) usa el chat y los comandos, pero no recibe la música: los paquetes de recursos de Java no le llegan.
- Los datos (UruCoins, memoria de cada jugador, temas bajados y paquetes) quedan en `datos/` y en la carpeta de CDisc, y no se suben al repo.
- Con el server en modo no oficial (`online-mode=false`), conviene AuthMe para que nadie entre con el nombre del admin.

## Créditos

Hecho por [@gauchitodev](https://github.com/gauchitodev) sobre [ClaudIA](https://github.com/gauchitodev/ClaudIA) y todos los que la hicieron posible.
