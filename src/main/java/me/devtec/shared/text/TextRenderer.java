package me.devtec.shared.text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import me.devtec.shared.annotations.Checkers;
import me.devtec.shared.annotations.Nonnull;
import me.devtec.shared.annotations.Nullable;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.utility.ColorUtils;
import me.devtec.shared.utility.StringUtils;
import me.devtec.shared.utility.StringUtils.FormatType;

/**
 * Renders text by applying named placeholders, optional dynamic values and
 * optional legacy/hex color processing.
 *
 * <p>The renderer is intentionally independent from the component JSON format.
 * Component-specific traversal is handled by {@code ComponentAPI}. A renderer
 * can therefore be reused for plain strings, config messages and component
 * trees without introducing a dependency from the text package back to the
 * component package.</p>
 *
 * <p>Instances are mutable and intended to be assembled once and then reused
 * while sending a message or processing one logical operation.</p>
 */
public class TextRenderer {

    /**
     * Defines when color codes are translated relative to placeholder
     * replacement.
     */
    public enum ColorMode {
        /** Do not process color codes. */
        NONE,
        /** Colorize the source text first and insert placeholder values afterwards. */
        BEFORE_PLACEHOLDERS,
        /** Replace placeholders first and colorize the resulting text afterwards. */
        AFTER_PLACEHOLDERS
    }

    /**
     * Supplies a dynamic placeholder value for a target UUID.
     */
    @FunctionalInterface
    public interface ValueProvider {
        @Nullable
        String resolve(@Nullable UUID target);
    }

    private static final TextRenderer EMPTY = new ImmutableEmptyTextRenderer();

    private final Map<String, ValueProvider> dynamicValues = new HashMap<>();
    private final Map<String, String> staticValues = new HashMap<>();

    private ColorMode colorMode = ColorMode.NONE;
    private UUID target;
    private List<String> ignoredColorTokens;

    /**
     * Creates a mutable renderer without placeholders, target or colorization.
     */
    public static TextRenderer create() {
        return new TextRenderer();
    }

    /**
     * Creates a mutable renderer already associated with a target UUID.
     */
    public static TextRenderer forTarget(@Nullable UUID target) {
        return new TextRenderer().target(target);
    }

    /**
     * Returns an immutable no-op renderer used by internal fallback paths.
     */
    public static TextRenderer empty() {
        return EMPTY;
    }

    /**
     * Returns whether this renderer currently has no custom placeholders.
     */
    public boolean isEmpty() {
        return dynamicValues.isEmpty() && staticValues.isEmpty();
    }

    /**
     * Returns the default target used by {@link #render(String)}.
     */
    @Nullable
    public UUID target() {
        return target;
    }

    /**
     * Sets the default target UUID used by dynamic placeholder providers.
     */
    public TextRenderer target(@Nullable UUID target) {
        this.target = target;
        return this;
    }

    /**
     * Returns the current color processing mode.
     */
    public ColorMode colorMode() {
        return colorMode;
    }

    /**
     * Sets the color processing mode.
     */
    public TextRenderer colorMode(@Nonnull ColorMode mode) {
        Checkers.nonNull(mode, "Color mode");
        colorMode = mode;
        return this;
    }

    /**
     * Replaces placeholders first and colorizes the final rendered text.
     */
    public TextRenderer colorize() {
        colorMode = ColorMode.AFTER_PLACEHOLDERS;
        return this;
    }

    /** Enables or disables final-result colorization. */
    public TextRenderer colorize(boolean enabled) {
        return enabled ? colorize() : plain();
    }

    /**
     * Colorizes the source text before placeholder values are inserted.
     *
     * <p>This is useful when replacement values already contain their own
     * formatting and must not be modified by the source colorization pass.</p>
     */
    public TextRenderer colorizeBeforePlaceholders() {
        colorMode = ColorMode.BEFORE_PLACEHOLDERS;
        return this;
    }

    /**
     * Disables color processing.
     */
    public TextRenderer plain() {
        colorMode = ColorMode.NONE;
        return this;
    }

    /**
     * Registers a dynamic placeholder using the conventional {@code {name}}
     * token syntax.
     */
    public TextRenderer placeholder(@Nonnull String name, @Nonnull ValueProvider provider) {
        return token(toPlaceholder(name), provider);
    }

    /**
     * Registers a static placeholder using the conventional {@code {name}}
     * token syntax.
     */
    public TextRenderer placeholder(@Nonnull String name, @Nonnull String value) {
        return token(toPlaceholder(name), value);
    }

    /**
     * Registers a numeric placeholder using the conventional {@code {name}}
     * token syntax.
     */
    public TextRenderer placeholder(@Nonnull String name, @Nonnull Number value) {
        Checkers.nonNull(value, "Placeholder value");
        return token(toPlaceholder(name), StringUtils.formatDouble(FormatType.NORMAL, value.doubleValue()));
    }

    /**
     * Registers an exact token without adding braces around it.
     */
    public TextRenderer token(@Nonnull String token, @Nonnull ValueProvider provider) {
        Checkers.nonNull(token, "Token");
        Checkers.nonNull(provider, "Placeholder provider");

        staticValues.remove(token);
        dynamicValues.put(token, provider);
        invalidateTokenCache();
        return this;
    }

    /**
     * Registers an exact static token without adding braces around it.
     */
    public TextRenderer token(@Nonnull String token, @Nonnull String value) {
        Checkers.nonNull(token, "Token");
        Checkers.nonNull(value, "Placeholder value");

        dynamicValues.remove(token);
        staticValues.put(token, value);
        invalidateTokenCache();
        return this;
    }

    /**
     * Registers an exact numeric token without adding braces around it.
     */
    public TextRenderer token(@Nonnull String token, @Nonnull Number value) {
        Checkers.nonNull(value, "Placeholder value");
        return token(token, value.toString());
    }

    /**
     * Removes a conventional {@code {name}} placeholder.
     */
    public boolean removePlaceholder(@Nonnull String name) {
        return removeToken(toPlaceholder(name));
    }

    /**
     * Removes an exact token.
     */
    public boolean removeToken(@Nonnull String token) {
        Checkers.nonNull(token, "Token");

        boolean removed = staticValues.remove(token) != null;
        removed = dynamicValues.remove(token) != null || removed;
        if (removed)
            invalidateTokenCache();
        return removed;
    }


    /**
     * Returns a static placeholder value using the conventional {@code {name}} token.
     * Dynamic providers are intentionally not evaluated by this method.
     */
    @Nullable
    public String placeholderValue(@Nonnull String name) {
        return tokenValue(toPlaceholder(name));
    }

    /**
     * Returns a static value registered for an exact token.
     * Dynamic providers are intentionally not evaluated by this method.
     */
    @Nullable
    public String tokenValue(@Nonnull String token) {
        Checkers.nonNull(token, "Token");
        return staticValues.get(token);
    }

    /**
     * Returns all registered exact token strings.
     */
    public Set<String> tokens() {
        if (dynamicValues.isEmpty())
            return new HashSet<>(staticValues.keySet());
        if (staticValues.isEmpty())
            return new HashSet<>(dynamicValues.keySet());

        Set<String> tokens = new HashSet<>(mapCapacity(dynamicValues.size() + staticValues.size()));
        tokens.addAll(dynamicValues.keySet());
        tokens.addAll(staticValues.keySet());
        return tokens;
    }

    /**
     * Renders text using this renderer's default target.
     */
    @Nullable
    public String render(@Nullable String text) {
        return render(text, target);
    }

    /**
     * Renders text using an explicit target UUID.
     */
    @Nullable
    public String render(@Nullable String text, @Nullable UUID target) {
        if (text == null || text.isEmpty())
            return text;

        StringContainer output = new StringContainer(text);
        normalizeEscapedNewLines(output);
        renderInto(output, target);
        return output.toString();
    }

    /**
     * Renders text while explicitly skipping color processing.
     */
    @Nullable
    public String renderPlain(@Nullable String text) {
        return renderPlain(text, target);
    }

    /**
     * Renders text with an explicit target while skipping color processing.
     */
    @Nullable
    public String renderPlain(@Nullable String text, @Nullable UUID target) {
        if (text == null || text.isEmpty())
            return text;

        StringContainer output = new StringContainer(text);
        normalizeEscapedNewLines(output);
        replacePlaceholders(output, target);
        return output.toString();
    }

    /**
     * Renders directly into an existing {@link StringContainer}.
     */
    public StringContainer renderInto(@Nullable StringContainer text, @Nullable UUID target) {
        if (text == null || text.isEmpty())
            return text;

        switch (colorMode) {
        case BEFORE_PLACEHOLDERS:
            colorizeSource(text);
            replacePlaceholders(text, target);
            break;
        case AFTER_PLACEHOLDERS:
            replacePlaceholders(text, target);
            ColorUtils.colorize(text, null);
            break;
        default:
            replacePlaceholders(text, target);
            break;
        }
        return text;
    }

    /**
     * Renders every entry in the supplied list in place.
     */
    public List<String> renderAll(@Nonnull List<String> lines, @Nullable UUID target) {
        Checkers.nonNull(lines, "Lines");
        for (int i = 0; i < lines.size(); ++i)
            lines.set(i, render(lines.get(i), target));
        return lines;
    }

    /**
     * Applies only placeholder replacement to an existing container.
     */
    protected StringContainer replacePlaceholders(@Nonnull StringContainer text, @Nullable UUID target) {
        if (!dynamicValues.isEmpty())
            for (Entry<String, ValueProvider> entry : dynamicValues.entrySet()) {
                String token = entry.getKey();
                if (text.indexOf(token) == -1)
                    continue;

                String value;
                try {
                    value = entry.getValue().resolve(target);
                } catch (RuntimeException exception) {
                    exception.printStackTrace();
                    continue;
                }

                if (value != null)
                    text.replace(token, value);
            }

        if (!staticValues.isEmpty())
            for (Entry<String, String> entry : staticValues.entrySet())
                if (text.indexOf(entry.getKey()) != -1)
                    text.replace(entry.getKey(), entry.getValue());

        return text;
    }

    private void colorizeSource(StringContainer text) {
        if (isEmpty()) {
            ColorUtils.colorize(text, null);
            return;
        }
        ColorUtils.colorize(text, ignoredColorTokens());
    }

    private List<String> ignoredColorTokens() {
        List<String> cached = ignoredColorTokens;
        if (cached != null)
            return cached;

        cached = new ArrayList<>(tokens());
        ignoredColorTokens = cached;
        return cached;
    }

    private void invalidateTokenCache() {
        ignoredColorTokens = null;
    }

    private static void normalizeEscapedNewLines(StringContainer text) {
        if (text.indexOf("\\n") != -1)
            text.replace("\\n", "\n");
    }

    private static String toPlaceholder(String name) {
        Checkers.nonNull(name, "Placeholder name");
        if (name.length() >= 2 && name.charAt(0) == '{' && name.charAt(name.length() - 1) == '}')
            return name;
        return '{' + name + '}';
    }

    private static int mapCapacity(int size) {
        if (size < 3)
            return size + 1;
        if (size >= 1 << 30)
            return Integer.MAX_VALUE;
        return (int) (size / 0.75F) + 1;
    }

    private static final class ImmutableEmptyTextRenderer extends TextRenderer {
        @Override
        public TextRenderer target(UUID target) {
            return this;
        }

        @Override
        public TextRenderer colorMode(ColorMode mode) {
            return this;
        }

        @Override
        public TextRenderer colorize() {
            return this;
        }

        @Override
        public TextRenderer colorize(boolean enabled) {
            return this;
        }

        @Override
        public TextRenderer colorizeBeforePlaceholders() {
            return this;
        }

        @Override
        public TextRenderer plain() {
            return this;
        }

        @Override
        public TextRenderer placeholder(String name, ValueProvider provider) {
            return this;
        }

        @Override
        public TextRenderer placeholder(String name, String value) {
            return this;
        }

        @Override
        public TextRenderer placeholder(String name, Number value) {
            return this;
        }

        @Override
        public TextRenderer token(String token, ValueProvider provider) {
            return this;
        }

        @Override
        public TextRenderer token(String token, String value) {
            return this;
        }

        @Override
        public TextRenderer token(String token, Number value) {
            return this;
        }

        @Override
        public boolean removePlaceholder(String name) {
            return false;
        }

        @Override
        public boolean removeToken(String token) {
            return false;
        }
    }
}
