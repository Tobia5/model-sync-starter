package io.modelsync.core;

import java.time.Instant;
import java.util.Objects;
public record FetchResult<T>(Status status, T value, String validator, Instant sourceChangedAt) {
    public enum Status { FOUND, NOT_MODIFIED, ABSENT }
    public FetchResult {
        Objects.requireNonNull(status);
        if(validator!=null && (validator.length()>1024 || validator.indexOf('\r')>=0 || validator.indexOf('\n')>=0))
            throw new SyncExceptions.Permanent("INVALID_VALIDATOR");
        if (status == Status.FOUND && value == null) throw new IllegalArgumentException("FOUND needs a value");
        if (status != Status.FOUND && value != null) throw new IllegalArgumentException("Unexpected value");
    }
    public static <T> FetchResult<T> found(T v, String etag) { return new FetchResult<>(Status.FOUND,v,etag,null); }
    public static <T> FetchResult<T> unchanged(String etag) { return new FetchResult<>(Status.NOT_MODIFIED,null,etag,null); }
    public static <T> FetchResult<T> absent() { return new FetchResult<>(Status.ABSENT,null,null,null); }
}
