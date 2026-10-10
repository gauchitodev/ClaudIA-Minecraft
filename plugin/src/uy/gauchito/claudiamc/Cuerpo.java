package uy.gauchito.claudiamc;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.UUID;

// El cuerpo de Claudia: un maniquí (sin IA de Minecraft) que se mueve a mano, con un globito de texto encima.
// Acompaña al último que le habló; si nadie le habla, se queda en el lobby.
final class Cuerpo {
    private static final double PASO = 0.28;      // bloques por tick al caminar (~ velocidad de un jugador)
    private static final double CERCA = 2.5;      // a esta distancia se queda quieta
    private static final double LEJOS = 20;       // más lejos (o en otro mundo) aparece al lado de un saque
    private static final long OLVIDO_MS = 5 * 60_000; // sin hablarle 5 minutos, vuelve al lobby

    private final JavaPlugin plugin;
    private final java.util.function.Supplier<Location> casa;
    private Mannequin cuerpo;
    private TextDisplay globo;
    private UUID siguiendo;
    private long ultimoContacto;
    private int globoHasta; // tick en el que se borra el globito
    private int tick;

    Cuerpo(JavaPlugin plugin, java.util.function.Supplier<Location> casa) {
        this.plugin = plugin;
        this.casa = casa;
    }

    void aparecer() {
        Location l = casa.get().clone().add(2, 0, -2);
        cuerpo = l.getWorld().spawn(l, Mannequin.class, m -> {
            m.setPersistent(false); // se crea en cada arranque; no queda guardada en el mundo
            m.setInvulnerable(true);
            m.setGravity(false);
            m.setSilent(true);
            m.setAI(false);
            m.setImmovable(true);
            m.setCollidable(false);
            m.customName(Component.text("ClaudIA", NamedTextColor.LIGHT_PURPLE));
            m.setCustomNameVisible(true);
            m.setDescription(Component.text("decile \"claudia\" en el chat", NamedTextColor.GRAY));
            String valor = plugin.getConfig().getString("skin.value"), firma = plugin.getConfig().getString("skin.signature");
            if (valor != null && firma != null)
                m.setProfile(ResolvableProfile.resolvableProfile().addProperty(new ProfileProperty("textures", valor, firma)).build());
        });
        globo = l.getWorld().spawn(l, TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setBillboard(Display.Billboard.CENTER);
            t.setLineWidth(180);
            t.setBackgroundColor(Color.fromARGB(160, 40, 0, 60));
            t.setTransformation(new Transformation(new Vector3f(0, 0.75f, 0), new AxisAngle4f(), new Vector3f(1, 1, 1), new AxisAngle4f()));
            t.text(Component.empty());
        });
        cuerpo.addPassenger(globo);
        Bukkit.getScheduler().runTaskTimer(plugin, this::moverse, 2L, 2L);
    }

    void desaparecer() {
        if (globo != null) globo.remove();
        if (cuerpo != null) cuerpo.remove();
    }

    org.bukkit.entity.Entity entidad() {
        return cuerpo;
    }

    void atender(Player p) {
        siguiendo = p.getUniqueId();
        ultimoContacto = System.currentTimeMillis();
    }

    void decir(String texto) {
        if (globo == null) return;
        globo.text(Component.text(texto, NamedTextColor.WHITE));
        globoHasta = tick + 40 + texto.length(); // ~4 s más un poquito por letra (cada tick de acá son 2)
        ultimoContacto = System.currentTimeMillis();
    }

    private void moverse() {
        tick++;
        if (cuerpo == null || !cuerpo.isValid()) return;
        if (globoHasta > 0 && tick > globoHasta) {
            globo.text(Component.empty());
            globoHasta = 0;
        }
        Player p = siguiendo == null ? null : Bukkit.getPlayer(siguiendo);
        if (p == null || System.currentTimeMillis() - ultimoContacto > OLVIDO_MS) {
            siguiendo = null;
            Location c = casa.get().clone().add(2, 0, -2);
            if (cuerpo.getWorld() != c.getWorld() || cuerpo.getLocation().distance(c) > 1) llevar(c);
            return;
        }
        Location yo = cuerpo.getLocation();
        Location el = p.getLocation();
        if (yo.getWorld() != el.getWorld() || yo.distance(el) > LEJOS) {
            Vector atras = el.getDirection().setY(0);
            if (atras.lengthSquared() < 1e-4) atras = new Vector(0, 0, 1);
            llevar(el.clone().subtract(atras.normalize().multiply(CERCA)));
            return;
        }
        Vector hacia = el.toVector().subtract(yo.toVector());
        Location nueva = yo.clone();
        double dist = hacia.length();
        if (dist > CERCA) nueva.add(hacia.clone().normalize().multiply(Math.min(PASO * 2, dist - CERCA)));
        nueva.setDirection(el.toVector().subtract(nueva.toVector())); // siempre mirándolo
        llevar(nueva);
    }

    private void llevar(Location l) {
        cuerpo.teleport(l, TeleportFlag.EntityState.RETAIN_PASSENGERS);
    }
}
