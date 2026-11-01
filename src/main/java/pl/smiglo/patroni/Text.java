package pl.smiglo.patroni;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern LEGACY = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    private Text() {
    }

    /** Parsuje &c, &#RRGGBB oraz MiniMessage. */
    public static Component parse(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        String s = input.replace('§', '&');
        s = HEX.matcher(s).replaceAll("<reset><#$1>");
        Matcher m = LEGACY.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            char c = Character.toLowerCase(m.group(1).charAt(0));
            m.appendReplacement(sb, Matcher.quoteReplacement(tag(c)));
        }
        m.appendTail(sb);
        return MM.deserialize(sb.toString());
    }

    /** Jak parse, ale bez domyslnej kursywy (nazwy i lore przedmiotow). */
    public static Component item(String input) {
        return Component.empty().decoration(TextDecoration.ITALIC, false).append(parse(input));
    }

    private static String tag(char c) {
        switch (c) {
            case '0': return "<reset><black>";
            case '1': return "<reset><dark_blue>";
            case '2': return "<reset><dark_green>";
            case '3': return "<reset><dark_aqua>";
            case '4': return "<reset><dark_red>";
            case '5': return "<reset><dark_purple>";
            case '6': return "<reset><gold>";
            case '7': return "<reset><gray>";
            case '8': return "<reset><dark_gray>";
            case '9': return "<reset><blue>";
            case 'a': return "<reset><green>";
            case 'b': return "<reset><aqua>";
            case 'c': return "<reset><red>";
            case 'd': return "<reset><light_purple>";
            case 'e': return "<reset><yellow>";
            case 'f': return "<reset><white>";
            case 'k': return "<obfuscated>";
            case 'l': return "<bold>";
            case 'm': return "<strikethrough>";
            case 'n': return "<underlined>";
            case 'o': return "<italic>";
            case 'r': return "<reset>";
            default: return "";
        }
    }

    /** 2d 3h 4m */
    public static String duration(long ms) {
        long s = Math.max(0, ms / 1000);
        long d = s / 86400;
        long h = (s % 86400) / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (d > 0) return d + "d " + h + "h " + m + "m";
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + sec + "s";
        return sec + "s";
    }

    /** mm:ss albo h:mm:ss */
    public static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, sec);
        return String.format("%02d:%02d", m, sec);
    }
}
