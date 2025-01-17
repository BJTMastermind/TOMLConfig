package red.jackf.tomlconfig;

import red.jackf.tomlconfig.annotations.Config;
import red.jackf.tomlconfig.data.TOMLValue;
import red.jackf.tomlconfig.exceptions.ParsingException;
import red.jackf.tomlconfig.exceptions.TokenizationException;
import red.jackf.tomlconfig.parser.TOMLParser;
import red.jackf.tomlconfig.reflections.ClassPopulator;
import red.jackf.tomlconfig.reflections.ReflectionUtil;
import red.jackf.tomlconfig.reflections.mapping.Mapping;
import red.jackf.tomlconfig.settings.FailMode;
import red.jackf.tomlconfig.settings.KeySortMode;
import red.jackf.tomlconfig.writer.TOMLWriter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

/**
 * <p>Contains all methods necessary to read and write configuration files.</p>
 * <p>To get started, you should create an instance of this class:</p>
 * <pre>
 *     private static final TOMLConfig CONFIG = TOMLConfig.get(); </pre>
 * <p>You can then load a {@link Config} specification using {@link #readConfig(Class)}:</p>
 * <pre>
 *     // Read the config from the default file location (the working directory)
 *     ExampleConfig config = CONFIG.readConfig(ExampleConfig.class); </pre>
 * <p>This will generate a default configuration file if none exists at that location, including any necessary
 * directories.</p>
 * <p>You can also load from a custom location using {@link #readConfig(Class, Path)}:</p>
 * <pre>
 *     private static final Path CONFIG_LOCATION = Paths.get("config", "exampleconf.toml"));
 *
 *      ...
 *
 *     ExampleConfig config = CONFIG.readConfig(ExampleConfig.class, CONFIG_LOCATION); </pre>
 * <p>Finally, you can also read directly from a String:</p>
 * <pre>
 *     String toml = "[settings]\n..."
 *     ExampleConfig config = CONFIG.readConfig(ExampleConfig.class, toml); </pre>
 * <p>If you change the config object inside of the application (such as a configuration menu or environment-based defaults)
 * you can write an updated version using {@link #writeConfig(Config)}:</p>
 * <pre>
 *     ExampleConfig config = new ExampleConfig();
 *
 *      ...
 *
 *     // Write the config to the default location (the working directory)
 *     CONFIG.writeConfig(config);
 *
 *     // Write the config to a custom location
 *     CONFIG.writeConfig(config, CONFIG_LOCATION);
 *
 *     // Write the config to a {@link Writer}. These will have .flush() and .close() ran.
 *     StringWriter writer = new StringWriter();
 *     CONFIG.writeConfig(config, writer);
 *     String toml = writer.toString(); </pre>
 * <p>If an object in your configuration requires special handling, you can use {@link #register(Class, Mapping)} to
 * register a custom mapping.</p>
 * <p>Other customization options are available in the {@link Builder}.</p>
 *
 * @see Config
 * @see ClassPopulator#register(Class, Mapping)
 */
public class TOMLConfig {
    private final TOMLParser parser;
    private final ClassPopulator classPopulator;
    private final TOMLWriter writer;
    private final FailMode failMode;

    private TOMLConfig(FailMode failMode, int indentationStep, int maxLineLength, KeySortMode keySortMode) {
        this.failMode = failMode;
        this.parser = new TOMLParser();
        this.classPopulator = new ClassPopulator();
        this.writer = new TOMLWriter(indentationStep, maxLineLength, keySortMode);
    }

    /**
     * Create a new TOMLConfig object with default settings.
     *
     * @return A default TOMLConfig object.
     */
    public static TOMLConfig get() {
        return new Builder().build();
    }

    /**
     * Create a TOMLConfig builder, allowing you to change it's behavior.
     *
     * @return A builder for a TOMLConfig object.
     * @see Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the default file name of the configuration file.
     *
     * @param spec The config's class file.
     * @return A String representing the default name of the config file.
     */
    public static String getFileName(Class<? extends Config> spec) {
        return spec.getSimpleName() + ".toml";
    }

    /**
     * Save a config object to disk in the default location (the current working directory). Creates any directories
     * that do not exist in the path.
     *
     * @param config Config object to save to disk.
     * @param <T>    Type parameter of the config object.
     */
    public <T extends Config> void writeConfig(T config) {
        writeConfig(config, Paths.get(getFileName(config.getClass())));
    }


    /**
     * Save a config object to disk at a custom location. Creates any directories that do not exist in the path.
     *
     * @param config   Config object to save to disk.
     * @param location Path to save the configuration file to.
     * @param <T>      Type parameter of the config object.
     */
    public <T extends Config> void writeConfig(T config, Path location) {
        try {
            Path parent = location.getParent();
            if (parent != null) Files.createDirectories(parent);
            BufferedWriter write = Files.newBufferedWriter(location, StandardCharsets.UTF_8);
            writeConfig(config, write);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Save a config object to a Java Writer, such as a StringWriter or BufferedWriter.
     *
     * @param config Config object to save to disk.
     * @param write  Writer object to write the file to.
     * @param <T>    Type parameter of the config object.
     */
    public <T extends Config> void writeConfig(T config, Writer write) {
        try {
            TOMLValue toml = classPopulator.fromObject(config);
            write.write(this.writer.writeToString(toml));
            write.flush();
            write.close();
        } catch (IOException | ParsingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Read a config from the default location on disk (the current working directory). Creates a default config if it
     * is not found, a long with any directories that do not exist in the path.
     *
     * @param spec Class of the configuration to build.
     * @param <T>  Type parameter of the config object.
     * @return An instance of the config object according to {@code spec} loaded from the default location, or a default
     * copy if none found.
     */
    public <T extends Config> T readConfig(Class<T> spec) {
        return readConfig(spec, Paths.get(getFileName(spec)));
    }


    /**
     * Read a config from a custom location on disk. Creates a default config if it
     * is not found, a long with any directories that do not exist in the path.
     *
     * @param spec     Class of the configuration to build.
     * @param location Path to the configuration file.
     * @param <T>      Type parameter of the config object.
     * @return An instance of the config object according to {@code spec} loaded from given location, or a default
     * copy if none found.
     */
    public <T extends Config> T readConfig(Class<T> spec, Path location) {
        try {
            if (Files.exists(location)) {
                BufferedReader reader = Files.newBufferedReader(location, StandardCharsets.UTF_8);
                String contents = reader.lines().collect(Collectors.joining("\n"));
                return readConfig(spec, contents);
            } else {
                Path parent = location.getParent();
                if (parent != null) Files.createDirectories(parent);
                T defaultConfig = ReflectionUtil.instantiate(spec);
                writeConfig(defaultConfig, location);
                defaultConfig.onLoad();
                return defaultConfig;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Read a config from a String. Does not create any files on disk.
     *
     * @param spec       Class of the configuration to build.
     * @param tomlString String representation of the config object.
     * @param <T>        Type parameter of the config object.
     * @return An instance of the config object according to the passed String.
     */
    public <T extends Config> T readConfig(Class<T> spec, String tomlString) {

        try {
            TOMLValue toml = parser.parse(tomlString);
            T config = classPopulator.toObject(spec, toml);
            config.onLoad();
            return config;
        } catch (ParsingException | TokenizationException e) {
            if (failMode == FailMode.THROW) throw new RuntimeException(e);
            else {
                e.printStackTrace(System.err);
                T defaultConfig = ReflectionUtil.instantiate(spec);
                defaultConfig.onLoad();
                return defaultConfig;
            }
        }
    }

    /**
     * Registers a custom mapping for a class/interface if found during [de]serialization.
     *
     * @param clazz   Clazz to use this mapping for.
     * @param mapping Instance of a {@link Mapping} designed for clazz.
     * @see ClassPopulator#register(Class, Mapping)
     * @see Mapping
     */
    public void register(Class<?> clazz, Mapping<?> mapping) {
        this.classPopulator.register(clazz, mapping);
    }

    /**
     * A builder for a TOMLConfig object. You should call {@link #build()} once any settings are finished being changed.
     */
    public static class Builder {
        private int indentationStep = 4;
        private int maxLineLength = 80;
        private FailMode readFailMode = FailMode.LOG_AND_LOAD_DEFAULT;
        private KeySortMode keySortMode = KeySortMode.DECLARATION_ORDER;

        private Builder() {
        }

        /**
         * <p>What behavior should occur if the config fails on loading.</p>
         * Options:
         * <ul>
         *     <li>FailMode.THROW - Throw the exception upwards through the call chain.
         *     <li>FailMode.LOG_AND_LOAD_DEFAULT - Log the exception to System.err and load a default copy of the config.
         * </ul>
         * <p>Default is {@code FailMode.LOG_AND_LOAD_DEFAULT}.</p>
         *
         * @param mode What should happen on loading failure
         * @return The Builder object
         */
        public Builder withReadFailMode(FailMode mode) {
            this.readFailMode = mode;
            return this;
        }

        /**
         * <p>Sets the maximum length that a <i>comment</i> is allowed to go to. Any longer, and the comment will be broken
         * up between words to fit if possible.</p>
         * <p>Default is 80 characters.</p>
         *
         * @param maxLineLength Maximum length a comment is allowed to go to
         * @return The Builder object
         */
        public Builder withMaxLineLength(int maxLineLength) {
            this.maxLineLength = maxLineLength;
            return this;
        }

        /**
         * <p>Sets the indentation step - this is incremented between table headers and in arrays.</p>
         * <p>Default is 4 spaces.</p>
         *
         * @param indentationStep How large the indentation step is, in space count
         * @return The Builder object.
         */
        public Builder withIndentationStep(int indentationStep) {
            this.indentationStep = indentationStep;
            return this;
        }

        /**
         * <p>How to sort the keys in config files.</p>
         * Options:
         * <ul>
         *     <li> KeySortMode.DECLARATION_ORDER - Sort keys by the order they are declared in the class.
         *     <li> KeySortMode.ALPHABETICAL_ORDER - Sort keys in alphabetical order.
         * </ul>
         * <p>Tables are always written after standard keys, and arrays of tables written after that irrespective of this
         * option.</p>
         * <p>Default is {@code KeySortMode.DECLARATION_ORDER}.</p>
         *
         * @param keySortMode Which mode to sort by. One of {@link KeySortMode}.
         * @return The Builder object.
         */
        public Builder withKeySortMode(KeySortMode keySortMode) {
            this.keySortMode = keySortMode;
            return this;
        }

        /**
         * Compile all settings and return a TOMLConfig object.
         *
         * @return A built TOMLConfig object
         */
        public TOMLConfig build() {
            return new TOMLConfig(readFailMode, indentationStep, maxLineLength, keySortMode);
        }
    }
}
