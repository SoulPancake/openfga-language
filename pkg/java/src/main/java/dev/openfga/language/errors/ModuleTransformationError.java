package dev.openfga.language.errors;

import java.util.List;

/**
 * Exception thrown when module transformation encounters one or more errors.
 */
public class ModuleTransformationError extends Exception {

    private final List<ParsingError> errors;

    public ModuleTransformationError(List<ParsingError> errors) {
        super(Errors.messagesFromErrors(errors));
        this.errors = errors;
    }

    public List<ParsingError> getErrors() {
        return errors;
    }
}
