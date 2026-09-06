package me.devtec.shared.examples;

import static org.junit.Assert.*;
import java.util.Arrays;
import org.junit.Test;
import me.devtec.shared.components.ComponentAPI;
import me.devtec.shared.components.base.Component;
import me.devtec.shared.components.decorations.ClickEvent;
import me.devtec.shared.components.decorations.HoverEvent;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.text.TextRenderer;

public class ComponentExamplesTest {
    @Test public void clickableComponentAndJsonRoundTrip() {
        Component button = new Component("Open menu")
            .setBold(true)
            .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/menu"))
            .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Component("Click here")));
        Component decoded = ComponentAPI.fromJson(button.toJsonMapWithExtras());
        // Parsed text may live in child components; render the whole tree.
        assertEquals(ComponentAPI.toString(button), ComponentAPI.toString(decoded));
        assertTrue(decoded.isBold());
        assertEquals("/menu", decoded.getClickEvent().getValue());
        assertEquals("Click here", ComponentAPI.toString(decoded.getHoverEvent().getValue()));
    }

    @Test public void placeholdersAndMessagesFromConfig() {
        TextRenderer renderer = TextRenderer.create().plain().placeholder("player", "Alice");
        try (Config config = new Config()) {
            config.set("welcome", "Welcome, {player}!");
            Component message = ComponentAPI.fromConfig(config, "welcome", renderer);
            assertEquals("Welcome, Alice!", ComponentAPI.toString(message));
            // Rendering a message does not replace the stored template.
            assertEquals("Welcome, {player}!", config.getString("welcome"));
        }
    }

    @Test public void multipleLinesAndStructuredJsonRendering() {
        TextRenderer renderer = TextRenderer.create().plain().placeholder("player", "Alice");
        Component lines = ComponentAPI.fromList(Arrays.asList("Hello {player}", "Second line"), renderer);
        assertEquals("Hello Alice\nSecond line", ComponentAPI.toString(lines));
        Component template = new Component("Profile of {player}")
            .setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/profile {player}"));
        Component rendered = ComponentAPI.fromJson(template.toJsonMapWithExtras(), renderer);
        assertEquals("/profile Alice", rendered.getClickEvent().getValue());
        assertEquals("Profile of {player}", template.getText());
    }
}
