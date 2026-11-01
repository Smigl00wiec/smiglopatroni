package pl.smiglo.patroni;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Random;

/** Wszystkie bonusy patronow oraz "Godziny Patrona". */
public class BonusService implements Listener {

    private static final String HEALTH_KEY = "bonus_health";

    private final SmigloPatroni plugin;
    private final Random rnd = new Random();
    private BukkitTask task;
    private boolean internal;

    public BonusService(SmigloPatroni plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) sync(p);
        }, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) applyHealth(p, 0);
    }

    // ---- pomocnicze ----

    private double d(String path, double def) {
        return plugin.getConfig().getDouble("bonuses." + path, def);
    }

    private int i(String path, int def) {
        return plugin.getConfig().getInt("bonuses." + path, def);
    }

    private boolean chance(double percent) {
        return percent > 0 && rnd.nextDouble() * 100.0 < percent;
    }

    private boolean has(Player p, String id) {
        String pid = plugin.patrons().patronId(p.getUniqueId());
        return pid != null && pid.equalsIgnoreCase(id);
    }

    private boolean god(String id) {
        return plugin.god().isGod(id);
    }

    // ---- efekty okresowe (co sekunde) ----

    public void sync(Player p) {
        // Freddy: dodatkowe serca
        double hearts = 0;
        if (has(p, "freddy")) hearts += d("freddy.patron-hearts", 1.0);
        if (god("freddy")) hearts += d("freddy.god-hearts", 2.0);
        applyHealth(p, hearts * 2.0);

        // Predkosc: Foxy (patron + bog) i Springtrap (patron, ponizej progu zycia)
        int speed = -1;
        if (has(p, "foxy")) speed = Math.max(speed, i("foxy.patron-speed-level", 1) - 1);
        if (god("foxy")) speed = Math.max(speed, i("foxy.god-speed-level", 2) - 1);
        if (has(p, "springtrap") && p.getHealth() < d("springtrap.patron-health-threshold-hearts", 5.0) * 2.0) {
            speed = Math.max(speed, i("springtrap.patron-speed-level", 3) - 1);
        }
        applyEffect(p, PotionEffectType.SPEED, speed);

        // Bonnie: sila dla wszystkich w Godzine Patrona
        if (god("bonnie")) {
            applyEffect(p, PotionEffectType.STRENGTH, i("bonnie.god-strength-level", 1) - 1);
        }
    }

    private void applyEffect(Player p, PotionEffectType type, int amplifier) {
        if (amplifier < 0) return;
        PotionEffect existing = p.getPotionEffect(type);
        if (existing != null) {
            if (existing.getAmplifier() > amplifier) return;
            if (existing.getAmplifier() == amplifier && existing.getDuration() > 45) return;
        }
        p.addPotionEffect(new PotionEffect(type, 45, amplifier, true, false, true));
    }

    private void applyHealth(Player p, double extra) {
        AttributeInstance attr = p.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        NamespacedKey key = new NamespacedKey(plugin, HEALTH_KEY);
        AttributeModifier current = null;
        for (AttributeModifier m : attr.getModifiers()) {
            if (key.equals(m.getKey())) {
                current = m;
                break;
            }
        }
        if (extra <= 0) {
            if (current != null) {
                attr.removeModifier(current);
                double max = attr.getValue();
                if (p.getHealth() > max) p.setHealth(max);
            }
            return;
        }
        if (current != null && current.getAmount() == extra) return;
        if (current != null) attr.removeModifier(current);
        attr.addTransientModifier(new AttributeModifier(key, extra, AttributeModifier.Operation.ADD_NUMBER));
    }

    // ---- eventy ----

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> sync(e.getPlayer()), 20L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> sync(e.getPlayer()), 5L);
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player pl) return pl;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player shooter) return shooter;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (internal) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;

        Player attacker = resolveAttacker(e.getDamager());
        Player victimPlayer = victim instanceof Player vp ? vp : null;
        boolean pvpOnly = plugin.getConfig().getBoolean("settings.pvp-only", true);
        if (pvpOnly && (attacker == null || victimPlayer == null)) return;
        if (attacker != null && attacker.equals(victim)) return;

        // Chica (patron): szansa na zablokowanie uderzenia
        if (victimPlayer != null && has(victimPlayer, "chica") && chance(d("chica.patron-block-chance", 10.0))) {
            e.setCancelled(true);
            victimPlayer.sendActionBar(plugin.msg("block-victim"));
            plugin.sound(victimPlayer, "block");
            if (attacker != null) {
                attacker.sendActionBar(plugin.msg("block-attacker"));
                plugin.sound(attacker, "block");
            }
            return;
        }
        if (attacker == null) return;

        // mnozniki obrazen
        double mult = 1.0;
        if (has(attacker, "bonnie")) {
            mult *= d("bonnie.patron-damage-multiplier", 1.2);
        }
        if (has(attacker, "springtrap")
                && attacker.getHealth() < d("springtrap.patron-health-threshold-hearts", 5.0) * 2.0) {
            mult *= d("springtrap.patron-damage-multiplier", 1.3);
        }
        boolean extraHit = false;
        if (god("springtrap") && chance(d("springtrap.god-extra-hit-chance", 5.0))) {
            mult *= d("springtrap.god-extra-hit-multiplier", 2.0);
            extraHit = true;
        }
        if (mult != 1.0) {
            e.setDamage(e.getDamage() * mult);
        }
        if (extraHit) {
            attacker.sendActionBar(plugin.msg("extra-hit"));
            plugin.sound(attacker, "extra-hit");
            if (victimPlayer != null) {
                victimPlayer.sendActionBar(plugin.msg("extra-hit"));
                plugin.sound(victimPlayer, "extra-hit");
            }
        }

        // Golden Freddy (patron): seria piorunow
        if (has(attacker, "goldenfreddy") && chance(d("goldenfreddy.patron-lightning-chance", 5.0))) {
            lightningSeries(attacker, victim);
        }
        // Golden Freddy (bog): lewitacja
        if (god("goldenfreddy") && chance(d("goldenfreddy.god-levitation-chance", 5.0))) {
            int level = Math.max(1, i("goldenfreddy.god-levitation-level", 1));
            int ticks = (int) (d("goldenfreddy.god-levitation-seconds", 3.0) * 20);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, ticks, level - 1));
        }
        // Chica (bog): oslabienie
        if (god("chica") && chance(d("chica.god-weakness-chance", 10.0))) {
            int level = Math.max(1, i("chica.god-weakness-level", 1));
            int ticks = (int) (d("chica.god-weakness-seconds", 5.0) * 20);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, ticks, level - 1));
        }
        // Marionetka (patron): zdjecie helmu
        if (victimPlayer != null && has(attacker, "marionetka") && chance(d("marionetka.patron-helmet-chance", 5.0))) {
            removeHelmet(attacker, victimPlayer);
        }
    }

    private void removeHelmet(Player attacker, Player victim) {
        ItemStack helmet = victim.getInventory().getHelmet();
        if (helmet == null || helmet.getType().isAir()) return;
        victim.getInventory().setHelmet(null);
        Map<Integer, ItemStack> left = victim.getInventory().addItem(helmet);
        for (ItemStack rest : left.values()) {
            victim.getWorld().dropItemNaturally(victim.getLocation(), rest);
        }
        plugin.sound(victim, "helmet-break");
        plugin.sound(attacker, "helmet-break");
    }

    private void lightningSeries(Player attacker, LivingEntity victim) {
        final int strikes = Math.max(1, i("goldenfreddy.patron-lightning-strikes", 3));
        final long delay = Math.max(1, i("goldenfreddy.patron-lightning-delay-ticks", 10));
        final double damage = d("goldenfreddy.patron-lightning-damage-hearts", 3.5) * 2.0;
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= strikes || !victim.isValid() || victim.isDead()) {
                    cancel();
                    return;
                }
                count++;
                victim.getWorld().strikeLightningEffect(victim.getLocation());
                trueDamage(attacker, victim, damage);
            }
        }.runTaskTimer(plugin, 0L, delay);
    }

    /** Stale obrazenia (omijaja pancerz) - dokladnie tyle serc, ile w configu. */
    private void trueDamage(Player attacker, LivingEntity victim, double amount) {
        if (victim.getHealth() > amount) {
            victim.setHealth(victim.getHealth() - amount);
            victim.playHurtAnimation(0f);
        } else {
            internal = true;
            try {
                victim.damage(10_000.0, attacker);
            } finally {
                internal = false;
            }
        }
    }

    // Marionetka (bog): fajerwerki co X sekund
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (!god("marionetka")) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.FIREWORK_ROCKET) return;
        final Player p = e.getPlayer();
        final int ticks = (int) (d("marionetka.god-firework-cooldown-seconds", 2.0) * 20);
        if (ticks <= 0) return;
        Bukkit.getScheduler().runTask(plugin, () -> p.setCooldown(Material.FIREWORK_ROCKET, ticks));
    }
}
