package me.devtec.shared.examples;

import static org.junit.Assert.*;
import java.io.File;
import java.util.Arrays;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.DataType;

public class ConfigExamplesTest {
    @Rule public TemporaryFolder files = new TemporaryFolder();

    @Test public void typedValuesListsAndSectionKeys() {
        try (Config config = new Config()) {
            config.set("server.name", "Lobby");
            config.set("server.slots", 100);
            config.set("server.enabled", true);
            config.set("server.worlds", Arrays.asList("world", "nether"));
            assertEquals("Lobby", config.getString("server.name"));
            assertEquals(100, config.getInt("server.slots"));
            assertTrue(config.getBoolean("server.enabled"));
            assertEquals(Arrays.asList("world", "nether"), config.getStringList("server.worlds"));
            assertTrue(config.getKeys("server").contains("slots"));
            config.remove("server.slots");
            assertFalse(config.existsKey("server.slots"));
        }
    }

    @Test public void yamlCommentsAndJsonRoundTrip() {
        try (Config config = Config.loadFromString("server:\n  name: Lobby\n  slots: 100\n")) {
            config.setComments("server.slots", Arrays.asList("# Maximum player count"));
            String yaml = config.toString(DataType.YAML);
            assertTrue(yaml.contains("# Maximum player count"));
            try (Config decoded = Config.loadFromString(config.toString(DataType.JSON))) {
                assertEquals(100, decoded.getInt("server.slots"));
                assertEquals("Lobby", decoded.getString("server.name"));
            }
        }
    }

    @Test public void saveAndReloadFile() throws Exception {
        File file = new File(files.getRoot(), "settings.yml");
        try (Config config = new Config(file)) {
            config.set("message", "Ahoj světe");
            config.save(DataType.YAML);
        }
        try (Config loaded = Config.loadFromFile(file)) {
            assertEquals("Ahoj světe", loaded.getString("message"));
        }
    }
}
