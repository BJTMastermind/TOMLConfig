/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package red.jackf.tomlconfig;

import org.junit.Test;
import red.jackf.tomlconfig.exceptions.ParsingException;
import red.jackf.tomlconfig.exceptions.TokenizationException;
import red.jackf.tomlconfig.parser.TOMLParser;
import red.jackf.tomlconfig.parser.data.TOMLTable;
import red.jackf.tomlconfig.reflections.ClassPopulator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public class TOMLConfigTest {

    @Test
    public void testConfig() throws TokenizationException, ParsingException, ReflectiveOperationException {
        //String file = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/testfile4.toml"))).lines().collect(Collectors.joining("\n"));
        ExampleConfig config = new ExampleConfig();
        TOMLTable configTOML = (TOMLTable) ClassPopulator.INSTANCE.fromObject(config);
        System.out.println("TOML: " + configTOML);
        ExampleConfig parsedConfig = (ExampleConfig) ClassPopulator.INSTANCE.toObject(ExampleConfig.class, configTOML);
        assert config != parsedConfig;
        assert config.equals(parsedConfig);
    }
}
