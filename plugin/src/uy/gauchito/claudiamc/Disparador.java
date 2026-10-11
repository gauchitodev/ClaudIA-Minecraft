package uy.gauchito.claudiamc;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

// Mapas de un jugador en un server con varios: sus bloques de comandos usan @a o @p, que en multijugador le caen a
// cualquiera (a todos, o al que esté más cerca del bloque, que suele estar escondido lejos del botón). Acá se anota
// quién apretó el último botón, palanca o placa en cada mapa "individual" y, si un bloque de comandos de ese mapa se
// ejecuta poco después, sus @a/@p pasan a ser esa persona.
final class Disparador implements Listener {
    private static final long VENTANA_MS = 4000;
    private static final Pattern SELECTOR = Pattern.compile("@[ap](\\[[^\\]]*\\])?");

    private record Toque(UUID jugador, long cuando) {}

    private final Set<String> mundosIndividuales; // nombres de mundo (carpetas)
    private final Map<World, Toque> ultimo = new HashMap<>();

    Disparador(Set<String> mundosIndividuales) {
        this.mundosIndividuales = mundosIndividuales;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void alTocar(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        if (b == null || !mundosIndividuales.contains(b.getWorld().getName())) return;
        Material m = b.getType();
        boolean dispara = e.getAction() == Action.PHYSICAL // placas de presión, hilos
                || (e.getAction() == Action.RIGHT_CLICK_BLOCK && (Tag.BUTTONS.isTagged(m) || m == Material.LEVER));
        if (dispara) ultimo.put(b.getWorld(), new Toque(e.getPlayer().getUniqueId(), System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void alEjecutar(ServerCommandEvent e) {
        if (!(e.getSender() instanceof BlockCommandSender bloque)) return;
        World w = bloque.getBlock().getWorld();
        if (!mundosIndividuales.contains(w.getName())) return;
        Toque t = ultimo.get(w);
        if (t == null || System.currentTimeMillis() - t.cuando() > VENTANA_MS) return;
        Player p = Bukkit.getPlayer(t.jugador());
        if (p == null || p.getWorld() != w) return;
        if (SELECTOR.matcher(e.getCommand()).find()) e.setCommand(SELECTOR.matcher(e.getCommand()).replaceAll(p.getName()));
    }
}
