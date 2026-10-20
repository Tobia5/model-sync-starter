package io.modelsync.sample;
import io.modelsync.core.application.*;
import io.modelsync.core.domain.*;

/** Only fields owned by the external system belong in this synchronization view. */
public record Customer(String displayName, String status) {}
