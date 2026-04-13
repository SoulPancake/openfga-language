package dev.openfga.language;

/**
 * Represents a module file with a name and its DSL contents.
 */
public class ModuleFile {
    private final String name;
    private final String contents;

    public ModuleFile(String name, String contents) {
        this.name = name;
        this.contents = contents;
    }

    public String getName() {
        return name;
    }

    public String getContents() {
        return contents;
    }
}
