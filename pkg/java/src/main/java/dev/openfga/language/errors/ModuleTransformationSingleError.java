package dev.openfga.language.errors;

/**
 * Represents a single error that occurred during transformation of module files.
 * Line and column numbers provided are zero-based.
 */
public class ModuleTransformationSingleError extends ParsingError {

    public ModuleTransformationSingleError() {}

    public ModuleTransformationSingleError(String msg, String file, StartEnd line, StartEnd column) {
        super("transformation", new ErrorProperties(line, column, msg));
        setFile(file);
    }
}
