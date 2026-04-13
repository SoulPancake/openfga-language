package dev.openfga.language;

import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import dev.openfga.language.errors.ModuleTransformationError;
import dev.openfga.language.util.ExpectedModuleError;
import dev.openfga.language.util.TestsData;
import dev.openfga.sdk.api.model.AuthorizationModel;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class ModuleToModelShould {

    @ParameterizedTest(name = "{0}")
    @MethodSource("moduleTestCases")
    public void transformModuleFilesToModel(
            String name, List<ModuleFile> modules, String json, boolean skip, List<ExpectedModuleError> expectedErrors)
            throws Exception {
        Assumptions.assumeFalse(skip);

        if (modules == null) {
            return;
        }

        if (expectedErrors == null || expectedErrors.isEmpty()) {
            var actual = ModuleTransformer.transformModuleFilesToModel(modules, "1.2");

            var expectedAuthorizationModel = JSON.parse(json, AuthorizationModel.class);
            var expectedJson = JSON.stringify(expectedAuthorizationModel);
            var actualJson = JSON.stringify(actual);

            assertThat(actualJson).isEqualTo(expectedJson);
        } else {
            var thrown = catchThrowable(() -> ModuleTransformer.transformModuleFilesToModel(modules, "1.2"));

            assertThat(thrown).isInstanceOf(ModuleTransformationError.class);

            var errorsCount = expectedErrors.size();

            var formattedErrors = expectedErrors.stream()
                    .map(error -> {
                        var errorType = "transformation";
                        if (error.getType() != null && !error.getType().isEmpty()) {
                            errorType = error.getType();
                        }

                        return String.format(
                                "%s error at line=%d, column=%d: %s",
                                errorType,
                                error.getLine().getStart(),
                                error.getColumn().getStart(),
                                error.getMessage());
                    })
                    .collect(joining("\n\t* "));

            var expectedMessage = String.format(
                    "%d error%s occurred:\n\t* %s\n\n", errorsCount, errorsCount > 1 ? "s" : "", formattedErrors);

            assertThat(thrown).hasMessage(expectedMessage);

            var exception = (ModuleTransformationError) thrown;
            assertThat(exception.getErrors()).hasSize(errorsCount);
        }
    }

    @Test
    public void allowCustomSchemaVersion() throws ModuleTransformationError {
        var modules = List.of(new ModuleFile("core.fga", "module core\n  type user"));

        var actual = ModuleTransformer.transformModuleFilesToModel(modules, "1.1");

        assertThat(actual.getSchemaVersion()).isEqualTo("1.1");
    }

    @Test
    public void emptyModuleListProducesEmptyModel() throws ModuleTransformationError {
        var actual = ModuleTransformer.transformModuleFilesToModel(new ArrayList<>(), "1.2");

        assertThat(actual.getSchemaVersion()).isEqualTo("1.2");
        assertThat(actual.getTypeDefinitions()).isEmpty();
        assertThat(actual.getConditions()).isEmpty();
    }

    @Test
    public void duplicateTypeDefinitionProducesError() {
        var modules =
                List.of(new ModuleFile("a.fga", "module a\ntype user"), new ModuleFile("b.fga", "module b\ntype user"));

        var thrown = catchThrowable(() -> ModuleTransformer.transformModuleFilesToModel(modules, "1.2"));

        assertThat(thrown).isInstanceOf(ModuleTransformationError.class);
        var exception = (ModuleTransformationError) thrown;
        assertThat(exception.getErrors()).hasSize(1);
        assertThat(exception.getErrors().get(0).getMessage()).isEqualTo("duplicate type definition user");
        assertThat(exception.getErrors().get(0).getFile()).isEqualTo("b.fga");
    }

    @Test
    public void duplicateConditionProducesError() {
        var modules = List.of(
                new ModuleFile("a.fga", "module a\ntype user\ncondition my_cond(x: int) {\n  x < 10\n}"),
                new ModuleFile("b.fga", "module b\ntype doc\ncondition my_cond(y: int) {\n  y < 5\n}"));

        var thrown = catchThrowable(() -> ModuleTransformer.transformModuleFilesToModel(modules, "1.2"));

        assertThat(thrown).isInstanceOf(ModuleTransformationError.class);
        var exception = (ModuleTransformationError) thrown;
        assertThat(exception.getErrors()).hasSize(1);
        assertThat(exception.getErrors().get(0).getMessage()).isEqualTo("duplicate condition my_cond");
        assertThat(exception.getErrors().get(0).getFile()).isEqualTo("b.fga");
    }

    @Test
    public void nonModularFileProducesError() {
        var modules = List.of(new ModuleFile("nomod.fga", "model\n  schema 1.1\ntype user"));

        var thrown = catchThrowable(() -> ModuleTransformer.transformModuleFilesToModel(modules, "1.2"));

        assertThat(thrown).isInstanceOf(ModuleTransformationError.class);
        var exception = (ModuleTransformationError) thrown;
        assertThat(exception.getErrors()).hasSize(1);
        assertThat(exception.getErrors().get(0).getMessage()).isEqualTo("file is not a module");
        assertThat(exception.getErrors().get(0).getFile()).isEqualTo("nomod.fga");
    }

    @Test
    public void syntaxErrorInModuleFileProducesError() {
        var modules = List.of(new ModuleFile("bad.fga", "module bad\ntype user\n  relations\n    define"));

        var thrown = catchThrowable(() -> ModuleTransformer.transformModuleFilesToModel(modules, "1.2"));

        assertThat(thrown).isInstanceOf(ModuleTransformationError.class);
        var exception = (ModuleTransformationError) thrown;
        assertThat(exception.getErrors()).isNotEmpty();
        assertThat(exception.getErrors().get(0).getFile()).isEqualTo("bad.fga");
    }

    @Test
    public void extendedTypeNotExistProducesError() {
        var modules = List.of(
                new ModuleFile("a.fga", "module a\nextend type nonexistent\n  relations\n    define r: [user]"));

        var thrown = catchThrowable(() -> ModuleTransformer.transformModuleFilesToModel(modules, "1.2"));

        assertThat(thrown).isInstanceOf(ModuleTransformationError.class);
        var exception = (ModuleTransformationError) thrown;
        assertThat(exception.getErrors()).isNotEmpty();
        var hasExtendError = exception.getErrors().stream()
                .anyMatch(e ->
                        e.getMessage() != null && e.getMessage().contains("extended type nonexistent does not exist"));
        assertThat(hasExtendError).isTrue();
    }

    @Test
    public void moduleFileAccessors() {
        var moduleFile = new ModuleFile("test.fga", "module test\ntype user");
        assertThat(moduleFile.getName()).isEqualTo("test.fga");
        assertThat(moduleFile.getContents()).isEqualTo("module test\ntype user");
    }

    @Test
    public void parseModularDslReturnsTypeDefExtensions() {
        var transformer = new DslToJsonTransformer();
        var result = transformer.parseModularDsl(
                "module test\ntype user\nextend type doc\n  relations\n    define viewer: [user]");

        // The modular result should be accessible
        assertThat(result.getAuthorizationModel()).isNotNull();
        assertThat(result.getTypeDefExtensions()).isNotNull();
        // "doc" is an extended type
        assertThat(result.getTypeDefExtensions()).containsKey("doc");
    }

    @Test
    public void parseModularDslSuccessCase() {
        var transformer = new DslToJsonTransformer();
        var result = transformer.parseModularDsl("module test\ntype user");

        assertThat(result.IsSuccess()).isTrue();
        assertThat(result.IsFailure()).isFalse();
        assertThat(result.getAuthorizationModel()).isNotNull();
        assertThat(result.getAuthorizationModel().getTypeDefinitions()).hasSize(1);
        assertThat(result.getTypeDefExtensions()).isEmpty();
    }

    private static Stream<Arguments> moduleTestCases() {
        return TestsData.MODULE_TEST_CASES.stream()
                .map(testCase -> arguments(
                        testCase.getName(),
                        testCase.getModules(),
                        testCase.getJson(),
                        testCase.isSkip(),
                        testCase.getExpectedErrors()));
    }
}
