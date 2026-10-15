package org.mandfer.practices.crypto;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Exercise: docs/32-crypto.md. Delete this line to begin.")
class DeriveTest {

    private static final byte[] IKM = "input key material".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SALT = "salt".getBytes(StandardCharsets.UTF_8);
    private static final byte[] INFO = "context info".getBytes(StandardCharsets.UTF_8);

    @Test
    void hkdf_returns_the_requested_length() {
        assertEquals(32, Derive.hkdf(IKM, SALT, INFO, 32).length);
        assertEquals(16, Derive.hkdf(IKM, SALT, INFO, 16).length);
    }

    /** A KDF is a function. Same inputs, same key, every time, on every machine. */
    @Test
    void hkdf_is_deterministic() {
        assertArrayEquals(Derive.hkdf(IKM, SALT, INFO, 32), Derive.hkdf(IKM, SALT, INFO, 32));
    }

    @Test
    void a_different_salt_derives_a_different_key() {
        assertFalse(java.util.Arrays.equals(
                Derive.hkdf(IKM, SALT, INFO, 32),
                Derive.hkdf(IKM, "other salt".getBytes(StandardCharsets.UTF_8), INFO, 32)));
    }

    /** The info parameter is domain separation: same secret, different purpose, different key. */
    @Test
    void a_different_info_derives_a_different_key() {
        assertFalse(java.util.Arrays.equals(
                Derive.hkdf(IKM, SALT, INFO, 32),
                Derive.hkdf(IKM, SALT, "other context".getBytes(StandardCharsets.UTF_8), 32)));
    }

    @Test
    void ml_kem_generates_a_usable_key_pair() {
        KeyPair keyPair = Derive.newKemKeyPair();

        assertEquals("ML-KEM", keyPair.getPublic().getAlgorithm());
        assertTrue(keyPair.getPublic().getEncoded().length > 1000,
                "an ML-KEM public key is over a kilobyte; an EC one is tens of bytes");
    }

    @Test
    void both_sides_of_a_kem_agree_on_the_secret() {
        assertTrue(Derive.sharedSecretRoundTrips(Derive.newKemKeyPair()));
    }

    @Test
    void tls_offers_the_classical_group() {
        List<String> groups = Derive.tlsNamedGroups();

        assertTrue(groups.contains("x25519"), groups.toString());
    }
}
