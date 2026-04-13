package dev.openfga.language;

import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import dev.openfga.language.errors.ModuleTransformationError;
import dev.openfga.language.util.ExpectedModuleError;
import dev.openfga.language.util.TestsData;
import dev.openfga.sdk.api.model.AuthorizationModel;
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
