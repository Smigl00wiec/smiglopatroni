package pl.smiglo.patroni;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Losowanie "Boga" co godzine, bossbar i odliczanie. */
public class GodManager implements Listener {

    private final SmigloPatroni plugin;
    private final Random rnd = new Random();

    private BossBar bar;
    private BukkitTask task;

    private String currentId;
    private long until;
    private long lastMark;
    private boolean drawing;

    private ZoneId zone = ZoneId.systemDefault();
    private LocalTime start = LocalTime.of(13, 30);
    private LocalTime end = LocalTime.of(22, 30);
    private int intervalMin = 60;

    public GodManager(SmigloPatroni plugin) {
        this.plugin = plugin;
    }

    public void start() {
        reload();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        hideBar();
    }

    public void reload() {
        hideBar();
        FileConfiguration c = plugin.getConfig();
        try {
            zone = ZoneId.of(c.getString("god.timezone", ZoneId.systemDefault().getId()));
        } catch (Exception e) {
            zone = ZoneId.systemDefault();
        }
        try {
            start = LocalTime.parse(c.getString("god.start", "13:30"));
            end = LocalTime.parse(c.getString("god.end", "22:30"));
        } catch (Exception e) {
            plugin.getLogger().warning("Zly format god.start / god.end (uzyj HH:mm).");
        }
        intervalMin = Math.max(1, c.getInt("god.interval-minutes", 60));

        BossBar.Color color = BossBar.Color.YELLOW;
        BossBar.Overlay overlay = BossBar.Overlay.PROGRESS;
        try {
            color = BossBar.Color.valueOf(c.getString("god.bossbar.color", "YELLOW").toUpperCase());
        } catch (Exception ignored) {
        }
        try {
            overlay = BossBar.Overlay.valueOf(c.getString("god.bossbar.overlay", "PROGRESS").toUpperCase());
        } catch (Exception ignored) {
        }
        bar = BossBar.bossBar(Component.empty(), 1f, color, overlay);

        FileConfiguration d = plugin.patrons().data();
        currentId = d.getString("god.current");
        until = d.getLong("god.until", 0L);
        lastMark = d.getLong("god.mark", 0L);
    }

    private void saveState() {
        FileConfiguration d = plugin.patrons().data();
        d.set("god.current", currentId);
        d.set("god.until", until);
        d.set("god.mark", lastMark);
        plugin.patrons().saveData();
    }

    // ---- stan ----

    public String currentId() {
        if (currentId != null && System.currentTimeMillis() < until) return currentId;
        return null;
    }

    public boolean isGod(String id) {
        String cur = currentId();
        return cur != null && cur.equalsIgnoreCase(id);
    }

    public Patron currentPatron() {
        String cur = currentId();
        return cur == null ? null : plugin.patrons().get(cur);
    }

    public long remaining() {
        return Math.max(0, until - System.currentTimeMillis());
    }

    // ---- harmonogram ----

    private List<Long> draws(LocalDate day) {
        List<Long> list = new ArrayList<>();
        LocalDateTime t = day.atTime(start);
        LocalDateTime e = day.atTime(end);
        while (t.isBefore(e)) {
            list.add(t.atZone(zone).toInstant().toEpochMilli());
            t = t.plusMinutes(intervalMin);
        }
        return list;
    }

    private long endMs(LocalDate day) {
        return day.atTime(end).atZone(zone).toInstant().toEpochMilli();
    }

    /** Najblizszy moment (losowanie albo koniec okna) po podanym czasie. */
    public long nextBoundary(long after) {
        LocalDate d = Instant.ofEpochMilli(after).atZone(zone).toLocalDate();
        long best = Long.MAX_VALUE;
        for (int i = 0; i <= 2; i++) {
            LocalDate day = d.plusDays(i);
            for (long x : draws(day)) {
                if (x > after && x < best) best = x;
            }
            long e = endMs(day);
            if (e > after && e < best) best = e;
        }
        return best;
    }

    private long lastDraw(long now) {
        LocalDate d = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        long best = -1;
        for (long x : draws(d)) {
            if (x <= now && x > best) best = x;
        }
        return best;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (currentId != null && now >= until) {
            currentId = null;
            saveState();
        }
        if (!drawing) {
            long mark = lastDraw(now);
            if (mark > 0 && mark > lastMark) {
                lastMark = mark;
                long expiry = nextBoundary(mark);
                saveState();
                if (expiry > now) {
                    startDraw(null, expiry);
                }
            }
        }
        updateBar(now);
    }

    // ---- reczne losowanie (/apatroni wylosuj) ----

    public boolean manualDraw(String forcedId) {
        long now = System.currentTimeMillis();
        long expiry = Math.min(nextBoundary(now), now + intervalMin * 60_000L);
        return startDraw(forcedId, expiry);
    }

    public boolean isDrawing() {
        return drawing;
    }

    // ---- losowanie z animacja ----

    private boolean startDraw(String forcedId, long expiry) {
        if (drawing) return false;
        final List<Patron> all = new ArrayList<>(plugin.patrons().all());
        if (all.isEmpty()) return false;

        Patron t = forcedId != null ? plugin.patrons().get(forcedId) : null;
        if (t == null) t = all.get(rnd.nextInt(all.size()));
        final Patron target = t;

        FileConfiguration c = plugin.getConfig();
        final int step = Math.max(1, c.getInt("god.draw.step-ticks", 3));
        final int total = Math.max(step, c.getInt("god.draw.duration-ticks", 60));
        final String cycleSub = c.getString("god.draw.cycle-subtitle", "&7Losowanie Boga...");
        drawing = true;

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (elapsed >= total) {
                    cancel();
                    finish(target, expiry);
                    return;
                }
                Patron shown = all.get(rnd.nextInt(all.size()));
                Title title = Title.title(Text.parse(shown.name), Text.parse(cycleSub),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(step * 50L + 150L), Duration.ZERO));
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.showTitle(title);
                    plugin.sound(p, "draw-tick", 1f, 0.8f + rnd.nextFloat() * 0.8f);
                }
                elapsed += step;
            }
        }.runTaskTimer(plugin, 0L, step);
        return true;
    }

    private void finish(Patron p, long expiry) {
        currentId = p.id;
        until = expiry;
        drawing = false;
        saveState();

        String sub = plugin.getConfig().getString("god.draw.final-subtitle", "{desc}").replace("{desc}", p.godDesc);
        Title title = Title.title(Text.parse(p.name), Text.parse(sub),
                Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(4), Duration.ofMillis(750)));
        for (Player pl : Bukkit.getOnlinePlayers()) {
            pl.showTitle(title);
            plugin.sound(pl, "draw-final");
        }
        Bukkit.broadcast(plugin.msg("god-drawn", "name", p.name, "desc", p.godDesc));
        updateBar(System.currentTimeMillis());
    }

    // ---- bossbar ----

    private void hideBar() {
        if (bar == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bar);
    }

    private void updateBar(long now) {
        if (bar == null) return;
        FileConfiguration c = plugin.getConfig();
        long intervalMs = intervalMin * 60_000L;

        if (currentId != null && now < until) {
            Patron p = plugin.patrons().get(currentId);
            long rem = until - now;
            String text = c.getString("god.bossbar.format", "&8| &fBóg: {name} &8| &f{desc} &8| &6({time})")
                    .replace("{name}", p != null ? p.name : currentId)
                    .replace("{desc}", p != null ? p.godDesc : "")
                    .replace("{time}", Text.clock(rem));
            bar.name(Text.parse(text));
            bar.progress((float) Math.max(0.0, Math.min(1.0, rem / (double) intervalMs)));
            for (Player pl : Bukkit.getOnlinePlayers()) pl.showBossBar(bar);
        } else if (c.getBoolean("god.bossbar.show-when-inactive", false)) {
            long rem = nextBoundary(now) - now;
            String text = c.getString("god.bossbar.inactive-format", "&8| &fBóg: &7brak &8| &6({time})")
                    .replace("{time}", Text.clock(rem));
            bar.name(Text.parse(text));
            bar.progress(1f);
            for (Player pl : Bukkit.getOnlinePlayers()) pl.showBossBar(bar);
        } else {
            hideBar();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        updateBar(System.currentTimeMillis());
    }
}
