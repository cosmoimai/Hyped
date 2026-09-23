package com.hyped.app.room.application;

import java.util.Objects;

public final class RoomOperationException extends RuntimeException {
    private final Kind kind;
    private final String code;
    private final String title;

    public RoomOperationException(Kind kind, String code, String title, String detail) {
        super(Objects.requireNonNull(detail, "detail"));
        this.kind = Objects.requireNonNull(kind, "kind");
        this.code = Objects.requireNonNull(code, "code");
        this.title = Objects.requireNonNull(title, "title");
    }

    public Kind kind() {
        return kind;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }

    public enum Kind {
        NOT_FOUND,
        FORBIDDEN,
        CONFLICT,
        PRECONDITION_FAILED,
        VALIDATION
    }
}
