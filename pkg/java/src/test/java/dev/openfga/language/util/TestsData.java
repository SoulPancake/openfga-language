package dev.openfga.language.util;

import static java.util.Collections.unmodifiableList;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.openfga.language.ModuleFile;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class TestsData {

    public static final String TRANSFORMER_CASES_FOLDER = "../../tests/data/transformer";
    public static final String MODULE_TRANSFORMER_CASES_FOLDER = "../../tests/data/transformer-module";
    public static final String DSL_SYNTAX_CASES_FILE = "../../tests/data/dsl-syntax-validation-cases.yaml";
    public static final String DSL_SEMANTIC_CASES_FILE = "../../tests/data/dsl-semantic-validation-cases.yaml";
    public static final String JSON_SYNTAX_TRANSFORMER_CASES_FILE =
            "../../tests/data/json-syntax-transformer-validation-cases.yaml";
    public static final String FGA_MOD_CASES_FILE = "../../tests/data/fga-mod-transformer-cases.yaml";
    public static final String JSON_VALIDATION_CASES_FILE = "../../tests/data/json-validation-cases.yaml";
    public static final String SKIP_FILE = "test.skip";
    public static final String AUTHORIZATION_MODEL_JSON_FILE = "authorization-model.json";
    public static final String AUTHORIZATION_MODEL_DSL_FILE = "authorization-model.fga";

    public static final List<ValidTransformerTestCase> VALID_TRANSFORMER_TEST_CASES = loadValidTransformerTestCases();
    public static final List<DslSyntaxTestCase> DSL_SYNTAX_TEST_CASES = loadDslSyntaxTestCases();
    public static final List<MultipleInvalidDslSyntaxTestCase> DSL_VALIDATION_TEST_CASES = loadDslValidationTestCases();
    public static final List<JsonSyntaxTestCase> JSON_SYNTAX_TEST_CASES = loadJsonSyntaxTestCases();
    public static final List<FgaModTestCase> FGA_MOD_TRANSFORM_TEST_CASES = loadFgaModTransformTestCases();
    public static final List<JsonValidationTestCase> JSON_VALIDATION_TEST_CASES = loadJsonValidationTestCases();
    public static final List<ModuleTestCase> MODULE_TEST_CASES = loadModuleTestCases();

    private static List<ValidTransformerTestCase> loadValidTransformerTestCases() {
        var transformerCasesFolder = Paths.get(TRANSFORMER_CASES_FOLDER);

        List<ValidTransformerTestCase> cases = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(transformerCasesFolder)) {
            for (Path path : stream) {
                if (!Files.isDirectory(path)) {
                    continue;
                }

                var name = path.getFileName().toString();
                var skipFile = path.resolve(SKIP_FILE);
                var jsonFile = path.resolve(AUTHORIZATION_MODEL_JSON_FILE);
                var dslFile = path.resolve(AUTHORIZATION_MODEL_DSL_FILE);

                cases.add(new ValidTransformerTestCase(
                        name, Files.readString(dslFile), Files.readString(jsonFile), Files.exists(skipFile)));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return unmodifiableList(cases);
    }

    private static List<DslSyntaxTestCase> loadDslSyntaxTestCases() {
        var dslSyntaxCasesFile = Paths.get(DSL_SYNTAX_CASES_FILE);
        try {
            var json = Files.readString(dslSyntaxCasesFile);
            return YAML.parseList(json, new TypeReference<>() {});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<MultipleInvalidDslSyntaxTestCase> loadDslValidationTestCases() {
        var dslSyntaxCasesFile = Paths.get(DSL_SEMANTIC_CASES_FILE);
        try {
            var json = Files.readString(dslSyntaxCasesFile);
            return YAML.parseList(json, new TypeReference<>() {});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<JsonValidationTestCase> loadJsonValidationTestCases() {
        var jsonValidationCasesFile = Paths.get(JSON_VALIDATION_CASES_FILE);
        try {
            var yaml = Files.readString(jsonValidationCasesFile);
            return YAML.parseList(yaml, new TypeReference<>() {});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<JsonSyntaxTestCase> loadJsonSyntaxTestCases() {
        var dslSyntaxCasesFile = Paths.get(JSON_SYNTAX_TRANSFORMER_CASES_FILE);
        try {
            var json = Files.readString(dslSyntaxCasesFile);
            return YAML.parseList(json, new TypeReference<>() {});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<FgaModTestCase> loadFgaModTransformTestCases() {
        var fgaModCasesFile = Paths.get(FGA_MOD_CASES_FILE);
        try {
            var yaml = Files.readString(fgaModCasesFile);
            return YAML.parseList(yaml, new TypeReference<>() {});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<ModuleTestCase> loadModuleTestCases() {
        var testDataPath = Paths.get(MODULE_TRANSFORMER_CASES_FOLDER);

        List<ModuleTestCase> cases = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(testDataPath)) {
            for (Path path : stream) {
                if (!Files.isDirectory(path)) {
                    continue;
                }

                var testCase = new ModuleTestCase();
                testCase.setName(path.getFileName().toString());

                var skipFile = path.resolve(SKIP_FILE);
                if (Files.exists(skipFile)) {
                    testCase.setSkip(true);
                }

                // Load authorization model JSON if it exists
                var modelFile = path.resolve(AUTHORIZATION_MODEL_JSON_FILE);
                if (Files.exists(modelFile)) {
                    testCase.setJson(Files.readString(modelFile));
                }

                // Load expected errors if they exist
                var errorsFile = path.resolve("expected_errors.json");
                if (Files.exists(errorsFile)) {
                    var errorsJson = Files.readString(errorsFile);
                    var mapper = new ObjectMapper();
                    List<ExpectedModuleError> errors =
                            mapper.readValue(errorsJson, new TypeReference<List<ExpectedModuleError>>() {});

                    // Filter out validation errors (those with metadata.errorType set)
                    // as Go does - only keep transformation errors
                    List<ExpectedModuleError> transformErrors = new ArrayList<>();
                    for (var error : errors) {
                        if (error.getMetadata() == null
                                || error.getMetadata().getErrorType() == null
                                || error.getMetadata().getErrorType().isEmpty()) {
                            transformErrors.add(error);
                        }
                    }

                    if (transformErrors.isEmpty()) {
                        testCase.setSkip(true);
                    } else {
                        testCase.setExpectedErrors(transformErrors);
                    }
                }

                // Load module files
                var moduleDirectory = path.resolve("module");
                if (Files.exists(moduleDirectory) && Files.isDirectory(moduleDirectory)) {
                    List<ModuleFile> modules = new ArrayList<>();
                    try (DirectoryStream<Path> moduleStream = Files.newDirectoryStream(moduleDirectory, "*.fga")) {
                        for (Path moduleFilePath : moduleStream) {
                            if (Files.isDirectory(moduleFilePath)) {
                                continue;
                            }
                            modules.add(new ModuleFile(
                                    moduleFilePath.getFileName().toString(), Files.readString(moduleFilePath)));
                        }
                    }
                    // Sort modules by name for consistent ordering
                    modules.sort((a, b) -> a.getName().compareTo(b.getName()));
                    testCase.setModules(modules);
                }

                cases.add(testCase);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return unmodifiableList(cases);
    }
}
