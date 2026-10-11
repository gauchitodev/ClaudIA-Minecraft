package uy.gauchito.claudiamc;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

// Puente entre el chat del server y el servicio de Claudia (servicio/index.js, en 127.0.0.1:8791).
// Manda lo que se dice en el chat y ejecuta lo que el servicio devuelve: hablar, susurrar o dar un disco de CDisc.
public final class ClaudiaMC extends JavaPlugin implements Listener {
    private static final String BASE = "http://127.0.0.1:8791";
    private static final Component PREFIJO = Component.text("<", NamedTextColor.WHITE)
            .append(Component.text("ClaudIA", NamedTextColor.LIGHT_PURPLE))
            .append(Component.text("> ", NamedTextColor.WHITE));

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final Gson gson = new Gson();
    private static final Key TEMA = Key.key("claudia", "tema");
    // El paquete con el tema que está sonando, para mandárselo también a los que entran después.
    private ResourcePackInfo packActual;
    private static final UUID PACK_MUSICA = UUID.fromString("c1a0d1a0-0000-4000-8000-00000000c1a0");
    private final Lobby lobby = new Lobby(this);
    private Cuerpo cuerpo;
    private Voz voz;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        lobby.cargar();
        if (lobby.activo()) {
            getServer().getPluginManager().registerEvents(lobby, this);
            getCommand("lobby").setExecutor(lobby);
        }
        // Mapas de un jugador: sus bloques de comandos le caen a quien apretó el botón (ver Disparador).
        java.util.Set<String> individuales = new java.util.HashSet<>();
        org.bukkit.configuration.ConfigurationSection ms = getConfig().getConfigurationSection("mundos");
        if (ms != null) for (String id : ms.getKeys(false))
            if (ms.getBoolean(id + ".individual")) individuales.add(ms.getString(id + ".carpeta", id));
        if (!individuales.isEmpty()) getServer().getPluginManager().registerEvents(new Disparador(individuales), this);
        cuerpo = new Cuerpo(this, () -> lobby.activo() ? lobby.spawnDelLobby() : spawnExacto());
        cuerpo.aparecer();
        try {
            voz = Voz.conectar(this, cuerpo::entidad);
        } catch (NoClassDefFoundError sinVoicechat) {
            voz = null; // sin Simple Voice Chat, Claudia habla solo por texto
        }
        getLogger().info(voz != null ? "Claudia tiene cuerpo y voz." : "Claudia tiene cuerpo (sin voz: falta Simple Voice Chat).");
        Bukkit.getScheduler().runTaskTimer(this, this::reponerBloques, 300L, 300L);
        // Lo que Claudia dice sola (la respuesta de la IA, el fin de una trivia) se busca cada 2 segundos.
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> pedir(HttpRequest.newBuilder(URI.create(BASE + "/pendientes")).GET()), 40L, 40L);
    }

    @Override
    public void onDisable() {
        if (cuerpo != null) cuerpo.desaparecer();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void alChatear(AsyncChatEvent e) {
        JsonObject ev = new JsonObject();
        ev.addProperty("tipo", "chat");
        ev.addProperty("jugador", e.getPlayer().getName());
        ev.addProperty("texto", PlainTextComponentSerializer.plainText().serialize(e.message()));
        enviar(ev);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void alEntrar(PlayerJoinEvent e) {
        JsonObject ev = new JsonObject();
        ev.addProperty("tipo", "entra");
        ev.addProperty("jugador", e.getPlayer().getName());
        enviar(ev);
        if (packActual != null) mandarPack(e.getPlayer(), packActual);
    }

    // Cuando un jugador terminó de cargar el paquete del tema nuevo, le suena (por el canal de discos, sin mods).
    @EventHandler
    public void alCargarPack(PlayerResourcePackStatusEvent e) {
        if (packActual == null || !e.getID().equals(packActual.id())) return;
        if (e.getStatus() != PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) return;
        e.getPlayer().stopSound(SoundStop.named(TEMA));
        e.getPlayer().playSound(Sound.sound(TEMA, Sound.Source.RECORD, 1f, 1f), Sound.Emitter.self());
    }

    // Vuelve a poner los bloques de config.yml que no estén (lo que haya en su lugar que no sea aire, no se toca).
    private void reponerBloques() {
        for (String linea : getConfig().getStringList("reponer")) {
            String[] p = linea.trim().split("\\s+", 4);
            try {
                int x = Integer.parseInt(p[0]), y = Integer.parseInt(p[1]), z = Integer.parseInt(p[2]);
                org.bukkit.World w = Bukkit.getWorlds().get(0);
                if (!w.isChunkLoaded(x >> 4, z >> 4)) continue; // sin nadie cerca no hace falta (y no carga el chunk)
                org.bukkit.block.Block b = w.getBlockAt(x, y, z);
                if (!b.getType().isAir()) continue;
                b.setBlockData(Bukkit.createBlockData(p[3]));
            } catch (Exception ex) {
                getLogger().warning("Línea mal escrita en reponer: " + linea);
            }
        }
    }

    // Paper corrige la altura del spawn con el mapa de alturas, que en mapas de aventura viene mal y los tira al vacío.
    // Sin lobby: los nuevos y los que mueren sin cama aparecen en el punto exacto de /setworldspawn.
    private static Location spawnExacto() {
        Location l = Bukkit.getWorlds().get(0).getSpawnLocation();
        return new Location(l.getWorld(), l.getBlockX() + 0.5, l.getBlockY(), l.getBlockZ() + 0.5, l.getYaw(), l.getPitch());
    }

    @EventHandler
    public void alAparecer(AsyncPlayerSpawnLocationEvent e) {
        if (!lobby.activo() && e.isNewPlayer()) e.setSpawnLocation(spawnExacto());
    }

    @EventHandler
    public void alRevivir(PlayerRespawnEvent e) {
        if (lobby.activo()) return; // con lobby, lo maneja Lobby
        if (e.getPlayer().getPotentialRespawnLocation() != null) return; // el mapa le puso un punto: se respeta
        if (!e.isBedSpawn() && !e.isAnchorSpawn()) e.setRespawnLocation(spawnExacto());
    }

    private void mandarPack(Player p, ResourcePackInfo info) {
        if (p.getName().startsWith(".")) return; // los de Bedrock (Geyser) no usan paquetes de Java
        p.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .replace(false)
                .required(false)
                .prompt(Component.text("Música de ClaudIA: aceptá para escuchar el tema", NamedTextColor.LIGHT_PURPLE)));
    }

    private void enviar(JsonObject ev) {
        JsonArray online = new JsonArray();
        for (Player p : List.copyOf(Bukkit.getOnlinePlayers())) online.add(p.getName());
        ev.add("online", online);
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> pedir(HttpRequest.newBuilder(URI.create(BASE + "/evento"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(ev)))));
    }

    // Corre en un hilo aparte; las acciones se ejecutan en el hilo principal del server.
    private void pedir(HttpRequest.Builder req) {
        try {
            HttpResponse<String> r = http.send(req.timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString());
            JsonArray acciones = gson.fromJson(r.body(), JsonObject.class).getAsJsonArray("acciones");
            if (acciones == null || acciones.isEmpty()) return;
            Bukkit.getScheduler().runTask(this, () -> {
                for (JsonElement a : acciones) ejecutar(a.getAsJsonObject());
            });
        } catch (Exception ex) {
            // El servicio apagado no es un error del server: Claudia simplemente no contesta.
        }
    }

    private void ejecutar(JsonObject a) {
        String tipo = a.get("tipo").getAsString();
        String texto = a.has("texto") ? a.get("texto").getAsString() : "";
        Player p = a.has("jugador") ? Bukkit.getPlayerExact(a.get("jugador").getAsString()) : null;
        switch (tipo) {
            case "decir" -> {
                for (String linea : texto.split("\n")) Bukkit.broadcast(PREFIJO.append(Component.text(linea.strip())));
            }
            case "privado" -> {
                if (p == null) return;
                for (String linea : texto.split("\n"))
                    p.sendMessage(PREFIJO.append(Component.text(linea.strip(), NamedTextColor.GRAY)));
            }
            case "disco" -> {
                if (p == null) return;
                darDisco(p);
                p.performCommand("cdisc create " + a.get("busqueda").getAsString());
            }
            case "musica" -> {
                // Id fijo: el paquete de música nuevo reemplaza al anterior de música y se suma al del mapa.
                packActual = ResourcePackInfo.resourcePackInfo(PACK_MUSICA,
                        URI.create(a.get("url").getAsString()), a.get("sha1").getAsString());
                for (Player o : Bukkit.getOnlinePlayers()) mandarPack(o, packActual);
            }
            case "parar" -> {
                packActual = null;
                for (Player o : Bukkit.getOnlinePlayers()) o.stopSound(SoundStop.named(TEMA));
            }
            case "atencion" -> {
                if (p != null) cuerpo.atender(p);
            }
            case "globo" -> cuerpo.decir(texto);
            case "voz" -> {
                if (voz != null) voz.decir(a.get("archivo").getAsString());
            }
            default -> getLogger().warning("Acción desconocida: " + tipo);
        }
    }

    // Deja un disco en la mano: lo que tenía pasa al inventario (o al piso si está lleno).
    private void darDisco(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack enMano = inv.getItemInMainHand();
        if (!enMano.getType().isAir()) {
            inv.setItemInMainHand(null);
            inv.addItem(enMano).values().forEach(sobra -> p.getWorld().dropItem(p.getLocation(), sobra));
        }
        inv.setItemInMainHand(new ItemStack(Material.MUSIC_DISC_13));
    }
}
