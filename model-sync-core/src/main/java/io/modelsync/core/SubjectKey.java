package io.modelsync.core;

import java.util.Objects;
/** An opaque stable subject reference. Do not put access tokens in IDs. */
public record SubjectKey(String tenant, String id) {
    public SubjectKey {
        tenant = checked(tenant, "tenant", 128);
        id = checked(id, "id", 256);
    }
    static String checked(String value, String name, int max) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > max || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid " + name);
        return value;
    }
}
