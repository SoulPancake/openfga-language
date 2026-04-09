package dev.openfga.language.util;

import dev.openfga.language.ModuleFile;
import java.util.ArrayList;
import java.util.List;

public class ModuleTestCase {
    private String name;
    private List<ModuleFile> modules;
    private String json;
    private boolean skip;
    private List<ExpectedModuleError> expectedErrors = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ModuleFile> getModules() {
        return modules;
    }

    public void setModules(List<ModuleFile> modules) {
        this.modules = modules;
    }

    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }

    public boolean isSkip() {
        return skip;
    }

    public void setSkip(boolean skip) {
        this.skip = skip;
    }

    public List<ExpectedModuleError> getExpectedErrors() {
        return expectedErrors;
    }

    public void setExpectedErrors(List<ExpectedModuleError> expectedErrors) {
        this.expectedErrors = expectedErrors;
    }
}
