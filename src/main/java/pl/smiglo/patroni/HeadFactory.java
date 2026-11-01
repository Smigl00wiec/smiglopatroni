package pl.smiglo.patroni;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HeadFactory {

    private final SmigloPatroni plugin;
    private final Map<String, PlayerProfile> cache = new ConcurrentHashMap<>();

    public HeadFactory(SmigloPatroni plugin) {
        this.plugin = plugin;
    }

    /** Pobiera w tle skiny graczy podanych w configu jako "owner". */
    public void preload() {
        cache.clear();
        final Set<String> owners = new HashSet<>();
        String dice = plugin.getConfig().getString("gui.dice.head.owner", "");
        if (dice != null && !dice.isBlank()) {
            owners.add(dice);
        }
        ConfigurationSection ps = plugin.getConfig().getConfigurationSection("patrons");
        if (ps != null) {
            for (String id : ps.getKeys(false)) {
                String o = ps.getString(id + ".head.owner", "");
                String t = ps.getString(id + ".head.texture", "");
                if (o != null && !o.isBlank() && (t == null || t.isBlank())) {
                    owners.add(o);
                }
            }
        }
        if (owners.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (String owner : owners) {
                try {
                    PlayerProfile pp = Bukkit.createProfile(owner);
                    if (pp.complete(true)) {
                        cache.put(owner.toLowerCase(Locale.ROOT), pp);
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    public ItemStack create(String owner, String texture) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (texture != null && !texture.isBlank()) {
            PlayerProfile pp = Bukkit.createProfile(
                    UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8)), "smiglo");
            pp.setProperty(new ProfileProperty("textures", texture.trim()));
            meta.setPlayerProfile(pp);
        } else if (owner != null && !owner.isBlank()) {
            PlayerProfile pp = cache.get(owner.toLowerCase(Locale.ROOT));
            if (pp == null) {
                pp = Bukkit.createProfile(owner);
            }
            meta.setPlayerProfile(pp);
        }
        item.setItemMeta(meta);
        return item;
    }
}
