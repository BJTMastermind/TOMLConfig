/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package red.jackf.tomlconfig;

import red.jackf.tomlconfig.annotations.Config;
import red.jackf.tomlconfig.data.TOMLValue;
import red.jackf.tomlconfig.exceptions.ParsingException;
import red.jackf.tomlconfig.exceptions.TokenizationException;
import red.jackf.tomlconfig.parser.TOMLParser;
import red.jackf.tomlconfig.reflections.ClassPopulator;
import red.jackf.tomlconfig.reflections.ReflectionUtil;
import red.jackf.tomlconfig.writer.TOMLWriter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

/**
 * This class is the primary entrypoint to which you'll be using TOMLConfig.
 */
public class TOMLConfig {
    private final TOMLParser parser;
    private final ClassPopulator classPopulator;
    private final TOMLWriter writer;
    private final FailMode failMode;

    private TOMLConfig(FailMode failMode, int indentationStep, int maxLineLength) {
        this.failMode = failMode;
        this.parser = new TOMLParser();
        this.classPopulator = new ClassPopulator();
        this.writer = new TOMLWriter(indentationStep, maxLineLength);

    }

    public static TOMLConfig get() {
        return new Builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    private static Path getPath(Config config) {
        return config.getDirectory().resolve(config.fileName() + ".toml");
    }

    public <T extends Config> void writeConfig(T config) {
        writeConfig(config, getPath(config));
    }

    public <T extends Config> void writeConfig(T config, Path location) {
        try {
            BufferedWriter writer = Files.newBufferedWriter(location, StandardCharsets.UTF_8);
            TOMLValue toml = classPopulator.fromObject(config);
            writer.write(this.writer.writeToString(toml));
            writer.flush();
            writer.close();
        } catch (IOException | ParsingException e) {
            throw new RuntimeException(e);
        }
    }

    public <T extends Config> T readConfig(Class<T> spec) {
        T defaultConfig = ReflectionUtil.instantiate(spec);
        try {
            BufferedReader reader = Files.newBufferedReader(getPath(defaultConfig), StandardCharsets.UTF_8);
            String contents = reader.lines().collect(Collectors.joining("\n"));
            return readConfig(contents, spec);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Load a config object from it's string representation.
     * @param contents Raw string of the config object; this is usually piped from a file
     * @param spec Class of the config file
     * @param <T> Type variable for the file
     * @return Instance of the config class, populated with values from {@code contents}
     */
    public <T extends Config> T readConfig(String contents, Class<T> spec) {

        try {
            TOMLValue toml = parser.parse(contents);
            return classPopulator.toObject(spec, toml);
        } catch (ParsingException | TokenizationException e) {
            if (failMode == FailMode.THROW) throw new RuntimeException(e);
            else {
                e.printStackTrace(System.err);
                return ReflectionUtil.instantiate(spec);
            }
        }
    }

    public enum FailMode {
        THROW, // Pass the exception upwards through the call stack.
        LOG_AND_LOAD_DEFAULT // Log the exception, and load the default config.
    }

    public static class Builder {
        private int indentationStep = 2;
        private int maxLineLength = 80;
        private FailMode readFailMode = FailMode.THROW;

        private Builder() {}

        /**
         * <p>What behavior should occur if the config fails on loading.</p>
         * Options:
         * <ul>
         *     <li>FailMode.THROW - Throw the exception upwards through the call chain.
         *     <li>FailMode.LOG_AND_LOAD_DEFAULT - Log the exception to System.err and load a default copy of the config.
         * </ul>
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
         * <p>Default is 2 spaces.</p>
         *
         * @param indentationStep How large the indentation step is, in space count
         * @return The Builder object.
         */
        public Builder withIndentationStep(int indentationStep) {
            this.indentationStep = indentationStep;
            return this;
        }

        /**
         * Compile all settings and return a TOMLConfig object.
         *
         * @return A built TOMLConfig object
         */
        public TOMLConfig build() {
            return new TOMLConfig(readFailMode, indentationStep, maxLineLength);
        }
    }
}
