package org.mandfer.practices.crypto;

import java.security.KeyPair;
import java.util.List;

/**
 * Key derivation and post-quantum key exchange, both of which became ordinary JDK APIs
 * between Java 21 and 25.
 *
 * Nothing in this repo does any cryptography, which is why this is new code. See
 * docs/32-crypto.md. NOTE: needs --release 25.
 *
 * TODO 1: hkdf() -- KDF.getInstance("HKDF-SHA256") with HKDFParameterSpec.ofExtract()
 *         .addIKM(..).addSalt(..).thenExpand(info, length). Final in Java 25 (JEP 510).
 *         Before this API, everyone wrote HKDF out of Mac by hand, and got the extract step
 *         subtly wrong. The test pins determinism, which is the property that matters.
 * TODO 2: newKemKeyPair() -- KeyPairGenerator.getInstance("ML-KEM"). Java 24, JEP 496.
 *         Print the encoded public key length and compare it with an RSA or EC one. The
 *         number is the entire practical story of post-quantum crypto.
 * TODO 3: sharedSecretRoundTrips() -- the KEM API (Java 21, JEP 452): newEncapsulator on the
 *         public key, newDecapsulator on the private one, and check both sides agree.
 *         Note what a KEM does NOT do: it does not encrypt your message. Write down what it
 *         gives you instead and what you still have to do with it.
 * TODO 4: tlsNamedGroups() -- what your JDK will actually negotiate. Then read docs/32 for
 *         why the answer changes in Java 27.
 */
public final class Derive {

    private Derive() {
    }

    /** HKDF-SHA256: extract with the salt, then expand to length bytes bound to info. */
    public static byte[] hkdf(byte[] inputKeyMaterial, byte[] salt, byte[] info, int length) {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** A fresh ML-KEM key pair. */
    public static KeyPair newKemKeyPair() {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** True when encapsulation and decapsulation produce the same shared secret. */
    public static boolean sharedSecretRoundTrips(KeyPair keyPair) {
        throw new UnsupportedOperationException("TODO 3");
    }

    /** The TLS named groups this JDK supports, in preference order. */
    public static List<String> tlsNamedGroups() {
        throw new UnsupportedOperationException("TODO 4");
    }
}
