package dev.roam.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayersTest {

    private static YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(text);
        return config;
    }

    @Test
    void mostSpecificLayerWinsPerSetting() throws Exception {
        var defaults = yaml("radius:\n  min: 100\n  max: 3000\ncooldown: 60\n");
        var world = yaml("radius:\n  max: 500\n");
        var group = yaml("cooldown: 5\n");

        Layers layers = new Layers(group, world, defaults);

        assertEquals(5, layers.getInt("cooldown", -1));      // from the group
        assertEquals(500, layers.getInt("radius.max", -1));  // from the world
        assertEquals(100, layers.getInt("radius.min", -1));  // the world did not set it, so the defaults are used
    }

    @Test
    void numbersAreReadWhateverWayTheyAreWritten() throws Exception {
        // These are the kind of values that used to be ignored without any message.
        var config = yaml("a: 50\nb: 50.0\nc: '50'\nd: 50.9\n");
        Layers layers = new Layers(config);

        assertEquals(50, layers.getInt("a", 0));
        assertEquals(50, layers.getInt("b", 0));
        assertEquals(50, layers.getInt("c", 0));
        assertEquals(50, layers.getInt("d", 0));
        assertEquals(50.0, layers.getDouble("c", 0));
    }

    @Test
    void missingAndBrokenValuesUseTheFallback() throws Exception {
        Layers layers = new Layers(yaml("x: hello\ny: maybe\n"), null);

        assertEquals(7, layers.getInt("x", 7));
        assertEquals(7, layers.getInt("missing", 7));
        assertFalse(layers.getBoolean("y", false));
        assertTrue(layers.getBoolean("missing", true));
    }

    @Test
    void booleansAcceptYesAndNo() throws Exception {
        Layers layers = new Layers(yaml("a: yes\nb: 'off'\nc: true\n"));

        assertTrue(layers.getBoolean("a", false));
        assertFalse(layers.getBoolean("b", true));
        assertTrue(layers.getBoolean("c", false));
    }

    @Test
    void listsAndSectionsComeFromTheFirstLayerThatHasThem() throws Exception {
        var defaults = yaml("blocked-biomes: [ocean]\ncenter:\n  x: 5\n  z: 6\n");
        var world = yaml("blocked-biomes: [river, swamp]\n");
        Layers layers = new Layers(world, defaults);

        assertEquals(2, layers.getStringList("blocked-biomes").size());
        assertEquals(5, layers.getSection("center").getInt("x"));
    }
}
