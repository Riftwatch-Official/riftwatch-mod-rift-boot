package net.riftwatch.boot.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

final class PackSignature {
    static final String ALGORITHM = "Ed25519";
    private static final int SIGNATURE_BYTES = 64;
    private static final List<String> EMBEDDED_KEYS = List.of(
            "MCowBQYDK2VwAyEARhxpZctbaeVhyhLTDHnX1Ycvqllr2+6ke5wNHp88+Us=",
            "MCowBQYDK2VwAyEA+mAhDI9VHOybQqOhYq44wG61jJC4ig3+V8yuQ7Ol3CQ=");

    private final List<PublicKey> keys;

    PackSignature(List<PublicKey> keys) {
        this.keys = List.copyOf(keys);
    }

    static PackSignature embedded() {
        return new PackSignature(EMBEDDED_KEYS.stream().map(PackSignature::decodeKey).toList());
    }

    static PublicKey decodeKey(String base64) {
        try {
            return KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Pack signing key unreadable", e);
        }
    }

    boolean verify(Path file, byte[] signatureFile) throws IOException {
        byte[] signature = decodeSignature(signatureFile);
        if (signature == null) {
            return false;
        }
        byte[] content = Files.readAllBytes(file);
        for (PublicKey key : keys) {
            if (verify(key, content, signature)) {
                return true;
            }
        }
        return false;
    }

    private static boolean verify(PublicKey key, byte[] content, byte[] signature) {
        try {
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(key);
            verifier.update(content);
            return verifier.verify(signature);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    static byte[] decodeSignature(byte[] raw) {
        if (raw == null) {
            return null;
        }
        if (raw.length == SIGNATURE_BYTES) {
            return raw;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(new String(raw, StandardCharsets.US_ASCII).trim());
            return decoded.length == SIGNATURE_BYTES ? decoded : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
