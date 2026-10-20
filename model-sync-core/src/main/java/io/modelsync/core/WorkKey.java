package io.modelsync.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
public record WorkKey(String binding, SubjectKey subject) {
    public WorkKey { binding = SubjectKey.checked(binding, "binding", 128); Objects.requireNonNull(subject); }
    /** Length-prefixing avoids ambiguous concatenation across tenants and bindings. */
    public String storageId() {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            for (String part : new String[]{binding, subject.tenant(), subject.id()}) {
                byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
                d.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); d.update(bytes);
            }
            return HexFormat.of().formatHex(d.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
