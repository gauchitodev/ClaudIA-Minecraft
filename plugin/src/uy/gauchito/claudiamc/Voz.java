package uy.gauchito.claudiamc;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Supplier;

// La voz de Claudia por Simple Voice Chat: el audio sale de su cuerpo y se escucha más fuerte cuanto más cerca.
// Solo la oyen los que tienen el mod de voz; los demás leen el globito y el chat. Sin el plugin voicechat, no hace nada.
final class Voz implements VoicechatPlugin {
    private static final float ALCANCE = 32;

    private final Supplier<Entity> cuerpo;
    private VoicechatServerApi api;
    private AudioPlayer sonando;

    private Voz(Supplier<Entity> cuerpo) {
        this.cuerpo = cuerpo;
    }

    static Voz conectar(org.bukkit.plugin.Plugin plugin, Supplier<Entity> cuerpo) {
        BukkitVoicechatService servicio = Bukkit.getServicesManager().load(BukkitVoicechatService.class);
        if (servicio == null) return null;
        Voz voz = new Voz(cuerpo);
        servicio.registerPlugin(voz);
        return voz;
    }

    @Override
    public String getPluginId() {
        return "claudiamc";
    }

    @Override
    public void registerEvents(EventRegistration registro) {
        registro.registerEvent(VoicechatServerStartedEvent.class, e -> api = e.getVoicechat());
    }

    // archivo: PCM 48 kHz mono de 16 bits (little endian) que armó el servicio. Se borra después de leerlo.
    void decir(String archivo) {
        Entity e = cuerpo.get();
        Path ruta = Path.of(archivo);
        try {
            if (api == null || e == null) return;
            byte[] bytes = Files.readAllBytes(ruta);
            short[] muestras = new short[bytes.length / 2];
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(muestras);
            if (sonando != null) sonando.stopPlaying(); // si habla de nuevo, corta lo anterior
            EntityAudioChannel canal = api.createEntityAudioChannel(UUID.randomUUID(), api.fromEntity(e));
            if (canal == null) return;
            canal.setDistance(ALCANCE);
            sonando = api.createAudioPlayer(canal, api.createEncoder(), muestras);
            sonando.startPlaying();
        } catch (Exception ex) {
            Bukkit.getLogger().warning("[ClaudiaMC] no pude reproducir la voz: " + ex.getMessage());
        } finally {
            try {
                Files.deleteIfExists(ruta);
            } catch (Exception ignorada) {
            }
        }
    }
}
