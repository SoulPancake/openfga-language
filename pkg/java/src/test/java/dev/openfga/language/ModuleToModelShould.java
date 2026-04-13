package dev.openfga.language;

import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import dev.openfga.language.errors.ModuleTransformationError;
import dev.openfga.language.errors.ModuleTransformationSingleError;
import dev.openfga.language.errors.StartEnd;
import dev.openfga.language.util.ExpectedModuleError;
import dev.openfga.language.util.TestsData;
import dev.openfga.sdk.api.model.AuthorizationModel;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Nested;
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
    public void extendTypeAddsRelationsToEmptyOriginal() throws ModuleTransformationError {
        // Base type has no relations; extension adds relations to it
        var modules = List.of(
                new ModuleFile("core.fga", "module core\ntype user"),
                new ModuleFile("ext.fga", "module ext\nextend type user\n  relations\n    define admin: [user]"));

        var actual = ModuleTransformer.transformModuleFilesToModel(modules, "1.2");

        assertThat(actual.getTypeDefinitions()).hasSize(1);
        var userType = actual.getTypeDefinitions().get(0);
        assertThat(userType.getType()).isEqualTo("user");
        assertThat(userType.getRelations()).containsKey("admin");
        // Source info should point to the extension file
        assertThat(userType.getMetadata()
                        .getRelations()
                        .get("admin")
                        .getSourceInfo()
                        .getFile())
                .isEqualTo("ext.fga");
    }

    @Test
    public void extendTypeWithDuplicateRelationProducesError() {
        // Base type has a relation; extension tries to add the same relation name
        var modules = List.of(
                new ModuleFile("core.fga", "module core\ntype user\n  relations\n    define viewer: [user]"),
                new ModuleFile("ext.fga", "module ext\nextend type user\n  relations\n    define viewer: [user]"));

        var thrown = catchThrowable(() -> ModuleTransformer.transformModuleFilesToModel(modules, "1.2"));

        assertThat(thrown).isInstanceOf(ModuleTransformationError.class);
        var exception = (ModuleTransformationError) thrown;
        var hasRelError = exception.getErrors().stream()
                .anyMatch(e -> e.getMessage() != null && e.getMessage().contains("relation viewer already exists"));
        assertThat(hasRelError).isTrue();
    }

    @Test
    public void extendTypeAddsRelationsToTypeWithExistingRelations() throws ModuleTransformationError {
        // Base type has relations; extension adds different relations
        var modules = List.of(
                new ModuleFile("core.fga", "module core\ntype user\n  relations\n    define viewer: [user]"),
                new ModuleFile("ext.fga", "module ext\nextend type user\n  relations\n    define editor: [user]"));

        var actual = ModuleTransformer.transformModuleFilesToModel(modules, "1.2");

        assertThat(actual.getTypeDefinitions()).hasSize(1);
        var userType = actual.getTypeDefinitions().get(0);
        assertThat(userType.getRelations()).containsKey("viewer");
        assertThat(userType.getRelations()).containsKey("editor");
        assertThat(userType.getMetadata()
                        .getRelations()
                        .get("editor")
                        .getSourceInfo()
                        .getFile())
                .isEqualTo("ext.fga");
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

    @Test
    public void moduleTransformationSingleErrorDefaultConstructor() {
        var error = new ModuleTransformationSingleError();
        assertThat(error.getMessage()).isNull();
        assertThat(error.getFile()).isNull();
    }

    @Test
    public void moduleTransformationSingleErrorParameterizedConstructor() {
        var line = new StartEnd(1, 1);
        var column = new StartEnd(5, 10);
        var error = new ModuleTransformationSingleError("test msg", "test.fga", line, column);

        assertThat(error.getMessage()).isEqualTo("test msg");
        assertThat(error.getFile()).isEqualTo("test.fga");
        assertThat(error.getLine()).isNotNull();
        assertThat(error.getColumn()).isNotNull();
        // toString should include the full message with type, line, column
        assertThat(error.toString()).contains("transformation error at line=1, column=5: test msg");
    }

    @Nested
    class HelperMethods {

        @Test
        public void getTypeLineNumber_found() {
            var lines = new String[] {"module core", "type user", "  relations"};
            assertThat(ModuleTransformer.getTypeLineNumber("user", lines)).isEqualTo(1);
        }

        @Test
        public void getTypeLineNumber_notFound() {
            var lines = new String[] {"module core", "type user"};
            assertThat(ModuleTransformer.getTypeLineNumber("nonexistent", lines))
                    .isEqualTo(-1);
        }

        @Test
        public void getExtendedTypeLineNumber_found() {
            var lines = new String[] {"module ext", "extend type user", "  relations"};
            assertThat(ModuleTransformer.getExtendedTypeLineNumber("user", lines))
                    .isEqualTo(1);
        }

        @Test
        public void getExtendedTypeLineNumber_notFound() {
            var lines = new String[] {"module ext", "type user"};
            assertThat(ModuleTransformer.getExtendedTypeLineNumber("nonexistent", lines))
                    .isEqualTo(-1);
        }

        @Test
        public void getConditionLineNumber_found() {
            var lines = new String[] {"module core", "type user", "condition my_cond(x: int) {"};
            assertThat(ModuleTransformer.getConditionLineNumber("my_cond", lines))
                    .isEqualTo(2);
        }

        @Test
        public void getConditionLineNumber_notFound() {
            var lines = new String[] {"module core", "type user"};
            assertThat(ModuleTransformer.getConditionLineNumber("missing", lines))
                    .isEqualTo(-1);
        }

        @Test
        public void getRelationLineNumber_found() {
            var lines = new String[] {"type user", "  relations", "    define viewer: [user]"};
            assertThat(ModuleTransformer.getRelationLineNumber("viewer", lines)).isEqualTo(2);
        }

        @Test
        public void getRelationLineNumber_notFound() {
            var lines = new String[] {"type user", "  relations"};
            assertThat(ModuleTransformer.getRelationLineNumber("missing", lines))
                    .isEqualTo(-1);
        }

        @Test
        public void constructLineAndColumnData_emptyLines() {
            var result = ModuleTransformer.constructLineAndColumnData(new String[] {}, 0, "test");
            assertThat(result[0].getStart()).isEqualTo(0);
            assertThat(result[0].getEnd()).isEqualTo(0);
            assertThat(result[1].getStart()).isEqualTo(0);
            assertThat(result[1].getEnd()).isEqualTo(0);
        }

        @Test
        public void constructLineAndColumnData_negativeLineIndex() {
            var lines = new String[] {"some content"};
            var result = ModuleTransformer.constructLineAndColumnData(lines, -1, "test");
            assertThat(result[0].getStart()).isEqualTo(0);
            assertThat(result[0].getEnd()).isEqualTo(0);
            assertThat(result[1].getStart()).isEqualTo(0);
            assertThat(result[1].getEnd()).isEqualTo(0);
        }

        @Test
        public void constructLineAndColumnData_symbolFound() {
            var lines = new String[] {"  type user"};
            var result = ModuleTransformer.constructLineAndColumnData(lines, 0, "user");
            assertThat(result[0].getStart()).isEqualTo(0);
            assertThat(result[0].getEnd()).isEqualTo(0);
            assertThat(result[1].getStart()).isEqualTo(7);
            assertThat(result[1].getEnd()).isEqualTo(11);
        }

        @Test
        public void constructLineAndColumnData_symbolNotFound() {
            var lines = new String[] {"  type user"};
            var result = ModuleTransformer.constructLineAndColumnData(lines, 0, "nonexistent");
            assertThat(result[0].getStart()).isEqualTo(0);
            assertThat(result[0].getEnd()).isEqualTo(0);
            // When symbol is not found, wordIdx defaults to 0
            assertThat(result[1].getStart()).isEqualTo(0);
        }
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
