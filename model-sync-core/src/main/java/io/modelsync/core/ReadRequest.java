package io.modelsync.core;

import java.util.Objects;
public record ReadRequest(SubjectKey key, String validator) {
    public ReadRequest { Objects.requireNonNull(key); }
}
