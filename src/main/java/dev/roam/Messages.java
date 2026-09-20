package dev.roam;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The texts in messages.yml. They are written in MiniMessage, and old style codes such as &amp;7 and
 * &amp;#RRGGBB work too. Placeholders are written as {@code <world>}.
 */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern CODE = Pattern.compile("&([0-9a-fk-orA-FK-OR])");
    private static final Map<Character, String> TAGS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"), Map.entry('3', "dark_aqua"),
            Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"), Map.entry('6', "gold"), Map.entry('7', "gray"),
            Map.entry('8', "dark_gray"), Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"), Map.entry('f', "white"),
            Map.entry('k', "obfuscated"), Map.entry('l', "bold"), Map.entry('m', "strikethrough"),
            Map.entry('n', "underlined"), Map.entry('o', "italic"), Map.entry('r', "reset"));

    private final RoamPlugin plugin;
    private FileConfiguration file = new YamlConfiguration();

    public Messages(RoamPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File target = new File(plugin.getDataFolder(), "messages.yml");
        if (!target.exists()) plugin.saveResource("messages.yml", false);
        file = YamlConfiguration.loadConfiguration(target);

        // Messages added in newer versions still work for servers with an older file.
        var defaults = plugin.getResource("messages.yml");
        if (defaults != null) {
            file.setDefaults(YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(defaults, java.nio.charset.StandardCharsets.UTF_8)));
        }
    }

    /** Turns old style codes into MiniMessage tags. */
    public static String convertLegacy(String text) {
        String result = HEX.matcher(text).replaceAll("<#$1>");
        return CODE.matcher(result).replaceAll(match -> Matcher.quoteReplacement(
                "<" + TAGS.get(Character.toLowerCase(match.group(1).charAt(0))) + ">"));
    }

    /** {@code pairs} are placeholder names and values: "world", "nether", "time", "5s". */
    public Component component(String key, String... pairs) {
        return parse(file.getString(key, ""), pairs);
    }

    public Component parse(String text, String... pairs) {
        List<TagResolver> resolvers = new ArrayList<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            resolvers.add(Placeholder.unparsed(pairs[i], pairs[i + 1]));
        }
        return MINI.deserialize(convertLegacy(text), resolvers.toArray(new TagResolver[0]));
    }

    public boolean has(String key) {
        return !file.getString(key, "").isEmpty();
    }

    /** Sends a message with the prefix. Empty messages are skipped, so any of them can be turned off. */
    public void send(CommandSender to, String key, String... pairs) {
        String text = file.getString(key, "");
        if (text.isEmpty()) return;
        to.sendMessage(parse(file.getString("prefix", "") + text, pairs));
    }
}
