package com.minichat.api.generation;

public enum GenerationStatus {
    QUEUED("queued"),
    STREAMING("streaming"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    CANCELED("canceled");

    private final String value;

    GenerationStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static GenerationStatus fromValue(String value) {
        for (GenerationStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown generation status: " + value);
    }
}
