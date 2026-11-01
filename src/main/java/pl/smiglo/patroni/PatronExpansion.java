package pl.smiglo.patroni;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Locale;

public class PatronExpansion extends PlaceholderExpansion {

    private final SmigloPatroni plugin;

    public PatronExpansion(SmigloPatroni plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return plugin.getConfig().getString("settings.identifier", "smiglo");
    }

    @Override
    public String getAuthor() {
        return "Smiglo";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    private String color(String raw) {
        String mode = plugin.getConfig().getString("settings.placeholder-color", "AMPERSAND").toUpperCase(Locale.ROOT);
        switch (mode) {
            case "SECTION":
                return LegacyComponentSerializer.builder().character('§').hexColors()
                        .useUnusualXRepeatedCharacterHexFormat().build().serialize(Text.parse(raw));
            case "PLAIN":
                return PlainTextComponentSerializer.plainText().serialize(Text.parse(raw));
            default:
                return raw;
        }
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String key = params.toLowerCase(Locale.ROOT);
        switch (key) {
            case "patron": {
                Patron p = player == null ? null : plugin.patrons().current(player.getUniqueId());
                return color(p != null ? p.name : plugin.getConfig().getString("settings.placeholder-none", "&7Brak"));
            }
            case "god": {
                Patron g = plugin.god().currentPatron();
                return color(g != null ? g.name : plugin.getConfig().getString("settings.placeholder-none", "&7Brak"));
            }
            case "god_time":
                return plugin.god().currentPatron() != null ? Text.clock(plugin.god().remaining()) : "-";
            default:
                break;
        }

        // przekazanie nieznanych placeholderow do innych identyfikatorow (np. smigloplayers)
        List<String> forward = plugin.getConfig().getStringList("settings.forward-placeholders");
        for (String id : forward) {
            if (id.equalsIgnoreCase(getIdentifier())) continue;
            String raw = "%" + id + "_" + params + "%";
            String res = PlaceholderAPI.setPlaceholders(player, raw);
            if (!res.equals(raw)) return res;
        }
        return null;
    }
}
