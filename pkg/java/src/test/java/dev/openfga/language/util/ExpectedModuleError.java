package dev.openfga.language.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.openfga.language.errors.StartEnd;

public final class ExpectedModuleError {
    @JsonProperty("msg")
    private String message;

    private StartEnd line;
    private StartEnd column;
    private String file;
    private String type;
    private ExpectedModuleErrorMetadata metadata;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public StartEnd getLine() {
        return line;
    }

    public void setLine(StartEnd line) {
        this.line = line;
    }

    public StartEnd getColumn() {
        return column;
    }

    public void setColumn(StartEnd column) {
        this.column = column;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public ExpectedModuleErrorMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(ExpectedModuleErrorMetadata metadata) {
        this.metadata = metadata;
    }

    public static class ExpectedModuleErrorMetadata {
        @JsonProperty("errorType")
        private String errorType;

        public String getErrorType() {
            return errorType;
        }

        public void setErrorType(String errorType) {
            this.errorType = errorType;
        }
    }
}
