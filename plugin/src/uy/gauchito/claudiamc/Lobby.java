package uy.gauchito.claudiamc;

import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

// Lobby con selector de mundos: todos entran al lobby, se paran en la placa del mapa que quieren y van a su inicio.
// /lobby los trae de vuelta. Los mapas y las placas salen de config.yml (secciones "lobby", "mundos" y "placas").
final class Lobby implements Listener, CommandExecutor {
    // Un mundo vacío: el lobby se construye a mano, flotando.
    static final class Vacio extends ChunkGenerator {}

    private record Mundo(String id, String nombre, String carpeta, String spawn) {}

    private final JavaPlugin plugin;
    private final Map<String, Mundo> mundos = new HashMap<>();
    private final Map<String, String> placas = new HashMap<>(); // "x y z" en el lobby -> id del mundo
    private String carpetaLobby;
    private String spawnLobby;

    Lobby(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    boolean activo() {
        return carpetaLobby != null;
    }

    // Carga (o crea) los mundos de config.yml. Va en onEnable, con el mundo principal ya cargado.
    void cargar() {
        ConfigurationSection lobby = plugin.getConfig().getConfigurationSection("lobby");
        if (lobby == null) return;
        carpetaLobby = lobby.getString("carpeta", "lobby");
        spawnLobby = lobby.getString("spawn", "0.5 65 0.5 180");
        if (Bukkit.getWorld(carpetaLobby) == null) {
            new WorldCreator(carpetaLobby).generator(new Vacio()).generateStructures(false).createWorld();
        }
        ConfigurationSection ms = plugin.getConfig().getConfigurationSection("mundos");
        if (ms != null) {
            for (String id : ms.getKeys(false)) {
                ConfigurationSection m = ms.getConfigurationSection(id);
                Mundo mundo = new Mundo(id, m.getString("nombre", id), m.getString("carpeta", id), m.getString("spawn"));
                mundos.put(id, mundo);
                World w = Bukkit.getWorld(mundo.carpeta());
                if (w == null) w = new WorldCreator(mundo.carpeta()).createWorld();
                // Dificultad propia del mapa (por ejemplo, uno de zombis en un server que en general es pacífico).
                String dif = m.getString("dificultad");
                if (w != null && dif != null) w.setDifficulty(Difficulty.valueOf(dif.toUpperCase()));
            }
        }
        for (String linea : plugin.getConfig().getStringList("placas")) {
            String[] p = linea.trim().split("\\s+");
            if (p.length == 4) placas.put(p[0] + " " + p[1] + " " + p[2], p[3]);
        }
        plugin.getLogger().info("Lobby '" + carpetaLobby + "' con " + mundos.size() + " mundos y " + placas.size() + " placas.");
    }

    // "x y z [yaw]" en un mundo. Las coordenadas se toman tal cual: Paper no las corrige con el mapa de alturas.
    private static Location lugar(World w, String texto) {
        String[] p = texto.trim().split("\\s+");
        float yaw = p.length > 3 ? Float.parseFloat(p[3]) : 0f;
        return new Location(w, Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]), yaw, 0f);
    }

    Location spawnDelLobby() {
        return lugar(Bukkit.getWorld(carpetaLobby), spawnLobby);
    }

    private Location spawnDe(Mundo m) {
        World w = Bukkit.getWorld(m.carpeta());
        if (m.spawn() != null) return lugar(w, m.spawn());
        Location s = w.getSpawnLocation();
        return new Location(w, s.getBlockX() + 0.5, s.getBlockY(), s.getBlockZ() + 0.5, s.getYaw(), 0f);
    }

    // Al cambiar de mapa se arranca limpio: sin lo que te dio el mapa anterior y en modo aventura (los admins no).
    private void llevar(Player p, Location destino) {
        p.getInventory().clear();
        p.getActivePotionEffects().forEach(ef -> p.removePotionEffect(ef.getType()));
        p.setFallDistance(0);
        p.teleport(destino);
        if (!p.isOp()) p.setGameMode(GameMode.ADVENTURE);
    }

    @EventHandler
    public void alAparecer(AsyncPlayerSpawnLocationEvent e) {
        e.setSpawnLocation(spawnDelLobby()); // todos entran al lobby, nuevos o no
    }

    @EventHandler
    public void alRevivir(PlayerRespawnEvent e) {
        if (e.isBedSpawn() || e.isAnchorSpawn()) return;
        World w = e.getPlayer().getWorld();
        for (Mundo m : mundos.values()) {
            if (Bukkit.getWorld(m.carpeta()) == w) {
                e.setRespawnLocation(spawnDe(m)); // reaparece en el inicio del mapa en el que está
                return;
            }
        }
        e.setRespawnLocation(spawnDelLobby());
    }

    @EventHandler
    public void alPisar(PlayerInteractEvent e) {
        if (e.getAction() != Action.PHYSICAL || e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();
        if (!b.getWorld().getName().equals(carpetaLobby)) return;
        Mundo m = mundos.get(placas.get(b.getX() + " " + b.getY() + " " + b.getZ()));
        if (m == null) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            llevar(p, spawnDe(m));
            p.sendMessage(Component.text("Entraste a " + m.nombre() + ". Para volver al lobby: /lobby", NamedTextColor.LIGHT_PURPLE));
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return false;
        llevar(p, spawnDelLobby());
        return true;
    }
}
