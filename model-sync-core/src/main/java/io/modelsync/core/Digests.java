package io.modelsync.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
public final class Digests {
    private Digests(){}
    /** Length-prefixed UTF-8; preserves null vs empty and avoids delimiter collisions.
     * Caller still defines semantic normalization and field order. */
    public static String sha256Fields(String... fields){
        try{
            MessageDigest hash=MessageDigest.getInstance("SHA-256");
            for(String field:fields){
                if(field==null){hash.update(ByteBuffer.allocate(4).putInt(-1).array());continue;}
                byte[] bytes=field.getBytes(StandardCharsets.UTF_8);
                hash.update(ByteBuffer.allocate(4).putInt(bytes.length).array());hash.update(bytes);
            }
            return HexFormat.of().formatHex(hash.digest());
        }catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
