package me.devtec.shared.examples;

import static org.junit.Assert.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.*;
import me.devtec.shared.components.ComponentAPI;
import me.devtec.shared.components.base.Component;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.messaging.*;
import me.devtec.shared.text.TextRenderer;

/** Uses a recording platform provider: no real player receives a message. */
public class MessagingExamplesTest {
    private final RecordingProvider recording = new RecordingProvider();
    private Object previousProvider;
    private Field providerField;

    @Before public void installTestPlatform() throws Exception {
        // Test isolation only. A real platform installs its provider during startup.
        providerField = Messenger.class.getDeclaredField("provider");
        providerField.setAccessible(true);
        previousProvider = providerField.get(null);
        Messenger.init(recording);
    }

    @After public void restorePlatform() throws Exception { providerField.set(null, previousProvider); }

    @Test public void chatToOnePlayerAndToGroup() {
        Messenger.send("Alice", new Component("Hello"));
        Messenger.send(Arrays.asList("Alice", "Bob"), new Component("Server restart in 5 minutes"));
        assertEquals(Arrays.asList("chat:Alice:Hello", "chat:Alice:Server restart in 5 minutes",
            "chat:Bob:Server restart in 5 minutes"), recording.messages);
    }

    @Test public void actionBarAndTitleWithTiming() {
        Messenger.actionBar("Alice", new Component("Coins: 100"));
        TitleTimes times = new TitleTimes(10, 60, 20);
        Messenger.title("Alice", new Component("Welcome"), new Component("Enjoy the game"), times);
        assertEquals(Arrays.asList("bar:Alice:Coins: 100", "title:Alice:Welcome:Enjoy the game"), recording.messages);
        assertEquals(60, recording.times.stay());
    }

    @Test public void configToRendererToComponentToMessenger() {
        try (Config config = new Config()) {
            config.set("welcome", "Welcome, {player}!");
            TextRenderer renderer = TextRenderer.create().plain().placeholder("player", "Alice");
            Component message = ComponentAPI.fromConfig(config, "welcome", renderer);
            Messenger.send("Alice", message);
            assertEquals(Arrays.asList("chat:Alice:Welcome, Alice!"), recording.messages);
        }
    }

    private static final class RecordingProvider implements MessengerProvider {
        final List<String> messages = new ArrayList<>();
        TitleTimes times;

        @Override public void send(Object receiver, Component component) {
            messages.add("chat:" + receiver + ":" + ComponentAPI.toString(component));
        }
        @Override public void actionBar(Object receiver, Component component) {
            messages.add("bar:" + receiver + ":" + ComponentAPI.toString(component));
        }
        @Override public void title(Object receiver, Component title, Component subtitle, TitleTimes times) {
            this.times = times;
            messages.add("title:" + receiver + ":" + ComponentAPI.toString(title) + ":" + ComponentAPI.toString(subtitle));
        }
    }
}
