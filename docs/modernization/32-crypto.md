# 32. Crypto: key derivation, post-quantum, PEM

**Java 21** (KEM API, JEP 452) · **Java 24** (ML-KEM and ML-DSA, JEPs 496 and 497) · **Java 25**
(KDF API final, JEP 510) · stage 21 of [the release-ordered track](INDEX.md) · no build change:
the repo already compiles at `--release 26`

New code: **`crypto/Derive.java`**, four TODOs, and `DeriveTest`, seven tests behind one
`@Disabled`. Nothing else in the repo does cryptography, so there's nothing to modernise and no
characterization step. The first commit is a missing test instead (exercise 1).

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line.

| Exercise | What you do | Theory it uses | Tests |
| --- | --- | --- | --- |
| [1. HKDF](#exercise-1-hkdf-and-the-test-thats-missing) | Add the RFC test vector, then implement TODO 1. Three commits. | T5, T6 | The known-answer test comes first, failing. |
| [2. ML-KEM](#exercise-2-ml-kem-todos-2-and-3) | TODOs 2 and 3: the key pair and the round trip. | T2, T4 | `DeriveTest`'s two ML-KEM tests. |
| [3. TLS](#exercise-3-what-tls-actually-uses-todo-4) | TODO 4: list what your JDK will negotiate. | T7 | `DeriveTest`'s TLS test. |
| [4. Write the answers here](#exercise-4-write-the-answers-here) | Five written answers. | all | None. |
| [5. What got worse](#exercise-5-fill-in-what-got-worse) | Write down what this costs. | all | None. |
| [6. Review questions](#exercise-6-review-questions) | Answer without looking, then check against the theory. | all | None. |

## Theory

Read the seven sections in order: each one builds on the one before. Here is the whole story in
one paragraph, so you know where it's going.

> Two parties who have never met want to talk privately over a wire that anyone can listen to.
> Three jobs come out of that: **agree** on a secret, **send** a secret, and **prove** who wrote a
> message (T1). A KEM is the "send a secret" job, packaged so that one side picks the key and the
> other side unwraps it (T2). Signatures prove authorship, and the JDK's design lets four very
> different algorithms share the same four lines of code (T3). The newest of those algorithms are
> post-quantum, and their cost is size: keys and signatures that are ten to a hundred times bigger
> (T4). A shared secret isn't a key yet: a KDF turns one secret into the keys you actually use
> (T5). None of this can be tested by "it looks random", because wrong crypto looks random too: it
> needs a known answer (T6). And an algorithm being in the JDK doesn't mean anything uses it yet
> (T7).

Every code sample below is a complete program unless it says otherwise. Save it as the class name
plus `.java` and run it with `java Name.java`; no build needed. Outputs marked *verified* were run
on JDK 26. Crypto output is random by design (keys, signatures, secrets), so the samples print only
what doesn't change between runs: sizes, algorithm names, and whether things match.

### T1. The problem: two strangers and an eavesdropper

Alice and Bob have never met and share no secret. Everything they send crosses a network that
anyone can read. They want three things, and each one is a different job:

| Job | Question it answers | Shape | JDK class |
| --- | --- | --- | --- |
| **key agreement** | "How do we both end up with the same secret?" | both sides contribute; each combines its own private key with the other's public key | `javax.crypto.KeyAgreement` |
| **key encapsulation (KEM)** | "How do I send you a secret only you can open?" | one side picks the secret and wraps it with the other's public key | `javax.crypto.KEM` (Java 21) |
| **signature** | "How do you know I wrote this, and that nobody changed it?" | the writer signs with a private key; anyone verifies with the public key | `java.security.Signature` |

All three rest on the same idea: a **key pair**. The public half can be shown to anyone. The
private half never leaves its owner. What one half does, only the other half can undo or check.

Here is key agreement with X25519, the curve most TLS connections use today. Each side sends only
its public key, and both end up with the same 32 bytes:

```java
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import javax.crypto.KeyAgreement;

public class Agree {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("X25519");
        KeyPair alice = gen.generateKeyPair();          // each side makes its own pair...
        KeyPair bob   = gen.generateKeyPair();          // ...and sends only the public half

        KeyAgreement a = KeyAgreement.getInstance("X25519");
        a.init(alice.getPrivate());
        a.doPhase(bob.getPublic(), true);               // Alice: my private + Bob's public
        byte[] aliceSecret = a.generateSecret();

        KeyAgreement b = KeyAgreement.getInstance("X25519");
        b.init(bob.getPrivate());
        b.doPhase(alice.getPublic(), true);             // Bob: my private + Alice's public
        byte[] bobSecret = b.generateSecret();

        System.out.println("public key sent over the wire: " + alice.getPublic().getEncoded().length + " bytes");
        System.out.println("shared secret: " + aliceSecret.length + " bytes");
        System.out.println("both sides equal? " + Arrays.equals(aliceSecret, bobSecret));
    }
}
```

```text
public key sent over the wire: 44 bytes                  (verified 2026-11-23)
shared secret: 32 bytes
both sides equal? true
```

The eavesdropper saw both public keys and still can't compute the secret: that needs one of the
private keys. (This is X25519, an elliptic curve. T4 is about why that sentence has an expiry
date.)

### T2. A KEM: one side picks the key, the other unwraps it

A **KEM** (key encapsulation mechanism) gives two parties the same random symmetric key, in a
different shape from T1:

1. Bob makes a key pair and publishes the public key.
2. Alice **encapsulates** with Bob's public key. She gets two things: a fresh random secret key,
   and a blob (the **encapsulation**) to send to Bob.
3. Bob **decapsulates** the blob with his private key, and gets the same secret key.

```text
Bob:    key pair ──── public key ────────────────────▶ Alice
Alice:  encapsulate(Bob's public)  →  secret key + blob
Alice:  ──────────────── blob ───────────────────────▶ Bob
Bob:    decapsulate(blob, Bob's private)  →  the same secret key
```

**It doesn't encrypt your message.** All a KEM produces is a shared random key. Encrypting
anything with it (and deriving the right keys from it first, T5) is still your job.

The API is Java 21 (JEP 452), three releases before ML-KEM. RSA key transport always had this
shape. JEP 452 named it, and that's what let **ML-KEM** arrive in 24 without a new interface. On
JDK 26 (verified): `ML-KEM` defaults to **ML-KEM-768**, with a **1206-byte** public key, and the
encapsulation blob is **1088 bytes**. Compare that with T1's 44-byte X25519 public key.

There's no runnable KEM sample here on purpose: the encapsulate/decapsulate round trip is
exactly TODO 3, and you write it in exercise 2.

### T3. Signatures: four algorithms, the same four lines

A **signature** is computed from the message and the private key, and checked with the message
and the public key. Change one byte of the message and the check fails. This program signs
`"hello"` with four algorithms and checks a tampered copy:

```java
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;

public class Sign {
    static void row(String label, String algorithm, KeyPair keys) throws Exception {
        byte[] message = "hello".getBytes(StandardCharsets.UTF_8);

        Signature signer = Signature.getInstance(algorithm);
        signer.initSign(keys.getPrivate());
        signer.update(message);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance(algorithm);
        verifier.initVerify(keys.getPublic());
        verifier.update(message);
        boolean ok = verifier.verify(signature);

        verifier.initVerify(keys.getPublic());
        verifier.update("hellO".getBytes(StandardCharsets.UTF_8));   // one letter changed
        boolean tampered = verifier.verify(signature);

        System.out.printf("%-26s public key %5d B   signature %5s B   verify %-5s tampered %s%n",
                label, keys.getPublic().getEncoded().length,
                algorithm.contains("ECDSA") ? "~" + signature.length : String.valueOf(signature.length),
                ok, tampered);
    }

    public static void main(String[] args) throws Exception {
        row("Ed25519", "Ed25519", KeyPairGenerator.getInstance("Ed25519").generateKeyPair());

        KeyPairGenerator ec = KeyPairGenerator.getInstance("EC");
        ec.initialize(new ECGenParameterSpec("secp256r1"));
        row("SHA256withECDSA, P-256", "SHA256withECDSA", ec.generateKeyPair());

        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(3072);
        row("SHA256withRSA, 3072-bit", "SHA256withRSA", rsa.generateKeyPair());

        row("ML-DSA (default)", "ML-DSA", KeyPairGenerator.getInstance("ML-DSA").generateKeyPair());

        // The defaults, when you don't say:
        System.out.println("default EC key:  " + KeyPairGenerator.getInstance("EC").generateKeyPair()
                .getPublic().getEncoded().length + " bytes");
        System.out.println("default RSA key: " + ((java.security.interfaces.RSAPublicKey)
                KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic()).getModulus().bitLength() + " bits");
    }
}
```

```text
Ed25519                    public key    44 B   signature    64 B   verify true  tampered false   (verified 2026-11-23)
SHA256withECDSA, P-256     public key    91 B   signature   ~71 B   verify true  tampered false
SHA256withRSA, 3072-bit    public key   422 B   signature   384 B   verify true  tampered false
ML-DSA (default)           public key  1974 B   signature  3309 B   verify true  tampered false
default EC key:  120 bytes
default RSA key: 3072 bits
```

The ECDSA signature size moves by a byte or two between runs (70 to 72 bytes), because its DER
encoding drops leading zero bytes. Everything else is fixed. The same table from the old version
of this doc (verified, JDK 26, default parameters):

| Algorithm | public key (encoded) | signature | verify | tampered |
| --- | --- | --- | --- | --- |
| `Ed25519` | 44 bytes | 64 bytes | true | false |
| `SHA256withECDSA`, P-256 | 91 bytes | ~71 bytes | true | false |
| `SHA256withRSA`, 3072-bit | 422 bytes | 384 bytes | true | false |
| **`ML-DSA`** (ML-DSA-65) | **1974 bytes** | **3309 bytes** | true | false |

The code is the same four lines for all four: `Signature.getInstance(alg)`, `initSign`, `update`,
`sign`. **That's the JCA design paying off**: ML-DSA slotted in behind an interface from 1997.
The engineering problem is the last row (T4).

Two defaults worth knowing, both verified: on JDK 26 `KeyPairGenerator.getInstance("EC")` gives
**P-384** (120-byte key), not P-256, and `"RSA"` gives **3072** bits. If you're filling in a size
table, set the parameters explicitly.

### T4. Post-quantum: why now, and what it costs

**ML-KEM** and **ML-DSA** are the NIST post-quantum standards (formerly Kyber and Dilithium), for
key exchange (T2) and for signatures (T3). **Post-quantum** means believed safe against a large
quantum computer, which would break RSA and elliptic curves: everything in T1 and the first three
rows of T3.

**Why now, when there is no quantum computer:** *store now, decrypt later.* Someone records your
TLS traffic today and decrypts it in 2040. Anything that has to stay secret for fifteen years is
already late. Signatures are less urgent: a signature verified today can't be forged
retroactively. So key exchange moves first.

**What it costs is size.** Keys and signatures got one to two orders of magnitude bigger:
44 bytes for an X25519 or Ed25519 public key, 1206 for ML-KEM-768, 1974 for ML-DSA-65.
Everything with a size assumption has to absorb that: TLS handshakes, certificate chains, JWT
headers, QR codes, embedded flash budgets.

And because ML-KEM is new, the first deployments are **hybrid**: a classical exchange and an
ML-KEM exchange done together, with both secrets mixed into the key. The connection stays safe
unless *both* are broken. Exercise 4 asks you why that's worth the extra bytes.

### T5. One secret, many keys: the KDF

T1 and T2 end with a shared secret. That isn't yet the key you encrypt with, for two reasons. Its
bytes may not be uniformly random (an X25519 output is a coordinate of a curve point, not 32 uniformly random bytes),
and you usually need **several** keys from it: one to encrypt, one to authenticate, one per
direction.

A **KDF** (key derivation function) turns one secret into one or more keys. **HKDF** (RFC 5869)
does it in two steps:

- **extract** mixes the input with a **salt** into a fixed-size key;
- **expand** stretches that to the length you ask for, bound to an **info** string.

```text
shared secret ──extract(salt)──▶ pseudorandom key ──expand(info = "enc", 32)──▶ encryption key
                                                  └─expand(info = "mac", 32)──▶ MAC key
```

**Domain separation** is what `info` is for: the same secret, a different purpose, a different
key. You must not use one key for both encryption and authentication. Change the `info`, and the
output has nothing in common with the other key, even though both came from one secret.

Before Java 25 there was no KDF abstraction in the JDK. It was previewed in 24 (JEP 478) and
final in 25 (JEP 510), as `javax.crypto.KDF`. Everyone built HKDF out of `Mac` by hand. Calling
the new API is TODO 1, so there's no sample of it here.

### T6. Testing crypto: only a known answer will do

Here's the rule for this whole doc:

> **Crypto that's wrong still produces bytes that look fine, so a test has to compare against a
> known answer.**

"Same input gives the same output" and "different input gives different output" are true of
almost any function. They don't tell you it's the right function. A **test vector** is a published
input with its known correct output, and it's the only kind of test that can tell a correct crypto
implementation from a plausible-looking wrong one.

This program builds a fake KDF (one hash, cut to length), runs four property checks against it,
and then does one known-answer check on SHA-256 itself:

```java
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

public class KnownAnswer {
    // A plausible "key derivation" that is not HKDF: one hash, cut to length.
    static byte[] notHkdf(byte[] ikm, byte[] salt, byte[] info, int len) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        sha.update(ikm);
        sha.update(salt);
        sha.update(info);
        return Arrays.copyOf(sha.digest(), len);
    }

    public static void main(String[] args) throws Exception {
        byte[] ikm = "secret".getBytes(StandardCharsets.UTF_8);
        byte[] salt = "salt".getBytes(StandardCharsets.UTF_8);
        byte[] info = "enc".getBytes(StandardCharsets.UTF_8);

        // Property checks: every one of them passes, and the function is still wrong.
        System.out.println("right length:        " + (notHkdf(ikm, salt, info, 16).length == 16));
        System.out.println("deterministic:       " + Arrays.equals(notHkdf(ikm, salt, info, 32), notHkdf(ikm, salt, info, 32)));
        System.out.println("salt changes it:     " + !Arrays.equals(notHkdf(ikm, salt, info, 32), notHkdf(ikm, "pepper".getBytes(), info, 32)));
        System.out.println("info changes it:     " + !Arrays.equals(notHkdf(ikm, salt, info, 32), notHkdf(ikm, salt, "mac".getBytes(), 32)));

        // A known-answer check: FIPS 180-2's published SHA-256 of "abc".
        String expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        byte[] actual = MessageDigest.getInstance("SHA-256").digest("abc".getBytes(StandardCharsets.US_ASCII));
        System.out.println("SHA-256(\"abc\") matches the published vector: " + HexFormat.of().formatHex(actual).equals(expected));
    }
}
```

```text
right length:        true                                (verified 2026-11-23)
deterministic:       true
salt changes it:     true
info changes it:     true
SHA-256("abc") matches the published vector: true
```

The four `true`s are worth nothing: `notHkdf` is wrong for every input. Those four checks are
exactly the four HKDF tests `DeriveTest` has today (exercise 1). The last line is worth
something, because the expected value came from the standard, not from running the code. For
HKDF, RFC 5869's Appendix A plays the same role.

### T7. In the JDK is not the same as in use

An algorithm can ship in the JDK and still not be used by anything you run. TLS is the clearest
case: JDK 26 can generate ML-KEM keys (T2), and its TLS connections are still entirely classical,
because nothing wires the two together. Exercise 3 has the evidence, and TODO 4 is how your code
asks.

The INDEX says Java 27 adds `X25519MLKEM768` (JEP 527), a **hybrid** (T4) of classical X25519 and
ML-KEM. There's no JDK 27 on this machine, so that's unverified here. **The useful thing is the
gap itself**: an algorithm shipping in the JDK and that algorithm being used by default are
releases apart.

The same gap shows up for features. **PEM encodings** (JEP 470 in 25, JEP 524 in 26) are still
preview on 26: the text format with `-----BEGIN PUBLIC KEY-----` lines that every other tool
already reads and writes.

```java
import java.security.KeyPairGenerator;
import java.security.PEMEncoder;
import java.security.PublicKey;

public class Pem {
    public static void main(String[] args) throws Exception {
        PublicKey key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic();
        String pem = PEMEncoder.of().encodeToString(key);
        String[] lines = pem.strip().split("\n");
        System.out.println(lines[0]);
        System.out.println("(" + (lines.length - 2) + " line(s) of Base64)");
        System.out.println(lines[lines.length - 1]);
    }
}
```

```sh
java Pem.java                                   # without the flag
java --enable-preview --source 26 Pem.java      # with it
```

```text
Pem.java:2: error: PEMEncoder is a preview API and is disabled by default.   (verified 2026-11-23)
...
-----BEGIN PUBLIC KEY-----
(1 line(s) of Base64)
-----END PUBLIC KEY-----
```

(The first block is trimmed. The Base64 line itself is the random key, so the sample doesn't
print it.) [Doc 33](33-preview-shelf.md) is about reading that preview signal.

## Practice

### Exercise 1: HKDF, and the test that's missing

`DeriveTest` has four HKDF tests: the requested length, determinism, salt changes the key, info
changes the key. **A correction to the old version:** it said those "pin" HKDF. They don't. This
is not HKDF:

```java
static byte[] hkdf(byte[] ikm, byte[] salt, byte[] info, int len) {    // NOT HKDF
    return Arrays.copyOf(sha256(ikm, salt, info), len);
}
```

and it **passes all four** (verified). It's deterministic, it gives the length you ask for (up to
32), and it changes when the salt or the info changes. It's also wrong for every input. T6 runs
the same trick.

Commits, in this order:

1. **Add the missing test first**, still failing (`TODO 1` throws). RFC 5869 Appendix A.1 is
   test case 1: the IKM is 22 bytes of `0x0b`, the salt is `0x00` to `0x0c`, the info is `0xf0` to
   `0xf9`, and the length is 42. The expected output is in the RFC: copy it from there, not from a
   blog. Use `HexFormat.of().parseHex(...)`. The `javax.crypto.KDF` result matches it (verified).
2. **Delete the `@Disabled`, implement TODO 1.** The javadoc on `Derive` names the calls. Notice
   that the class is `javax.crypto.KDF`, not `java.security`. Note what the API does with a
   length over 255 × 32 bytes, the RFC's limit: `InvalidAlgorithmParameterException`, with the
   message "The specified SunJCE provider does not support this input" (verified). The message
   doesn't say what's wrong. Write down whether your method should check first.
3. **Then question 2** in exercise 4: describe a real bug caused by leaving `info` empty (T5).

**Done when:** the RFC 5869 A.1 test is in its own commit before the implementation, all five
HKDF tests pass, and you've written down whether `hkdf` checks the length itself.

### Exercise 2: ML-KEM, TODOs 2 and 3

TODO 2 is the key pair. The test asks for an encoded public key over 1000 bytes. Verified on JDK
26: `ML-KEM` defaults to **ML-KEM-768**, with a **1206-byte** public key, and the encapsulation
blob is **1088 bytes**.

TODO 3 is the round trip (T2). What the encapsulator gives you is a `SecretKey` of algorithm
`Generic`, 32 bytes (verified). **Write down what you would do with it next** (the TODO asks
this). Hint: it isn't "use it as the AES key directly", and exercise 1 has the answer. That's why
these two APIs are in one exercise.

**Done when:** `ml_kem_generates_a_usable_key_pair` and `both_sides_of_a_kem_agree_on_the_secret`
pass, and the "what next" answer is written down.

### Exercise 3: what TLS actually uses, TODO 4

```java
SSLContext.getDefault().getSupportedSSLParameters().getNamedGroups()
```

On JDK 26 (verified):

```text
[x25519, secp256r1, secp384r1, secp521r1, x448, ffdhe2048, ffdhe3072, ffdhe4096, ffdhe6144, ffdhe8192]
```

**All classical.** JDK 26 can generate ML-KEM keys, and its TLS connections are entirely
classical, because nothing wires the two together. Asking for a hybrid group doesn't help:
`-Djdk.tls.namedGroups=X25519MLKEM768,x25519` leaves only `[x25519]` (verified). The unknown
name is dropped silently. `getNamedGroups()` is how you tell which side of T7's gap you're on.

**PEM encodings** are still preview on 26. Without the flag,
`PEMEncoder is a preview API and is disabled by default`. With `--enable-preview`,
`java.security.PEMEncoder.of()` works (both verified, and T7 runs it).

**Done when:** `tls_offers_the_classical_group` passes and `DeriveTest` is fully green with no
`@Disabled` left.

### Exercise 4: Write the answers here

1. **The size table, measured yourself.** Add the encoded private key sizes, and say why the
   ML-KEM private key is so much smaller than its public key on this JDK. (Look at what it
   encodes.) _(fill in)_
2. **`info` is domain separation.** Describe a concrete bug caused by omitting it. _(fill in)_
3. **Your own "store now, decrypt later" audit.** Name one thing your systems transmit that must
   stay secret for ten years, and say whether it's protected today. _(fill in)_
4. **Why hybrid rather than ML-KEM alone**, when ML-KEM is the NIST standard? _(fill in)_
5. **Your four HKDF tests passed a function that wasn't HKDF.** Name another place, in code you
   work on, where the tests check properties and never a known answer. _(fill in)_

### Exercise 5: fill in "What got worse"

- _(fill this in)_

Prompts: a 1206-byte public key in a TLS ClientHello: what does that do on a lossy mobile link?
A KEM only gives you a shared secret, so how many ways are there to get the next step wrong, and
does the JDK help with any of them? An unknown TLS group name is dropped without a warning: is
that the right behaviour for a security setting? Is "the JDK now has HKDF" a reason to stop using
a crypto library, or a reason to read its source?

### Exercise 6: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.
Any you got wrong, reread that section.

1. Name the three jobs two strangers need from cryptography, and the JDK class for each. (T1)
2. In key agreement, what does each side combine to get the shared secret, and what does the
   eavesdropper see? (T1)
3. In a KEM, who picks the secret, and what travels over the wire? (T2)
4. What does a KEM *not* do? (T2)
5. Why could ML-DSA be added without changing how you call `Signature`? (T3)
6. On JDK 26, what curve does `KeyPairGenerator.getInstance("EC")` give you by default? (T3)
7. What is "store now, decrypt later", and why does it make key exchange more urgent than
   signatures? (T4)
8. Roughly how much bigger is an ML-KEM-768 public key than an X25519 one? (T2, T4)
9. Why isn't a shared secret used directly as an encryption key? Give both reasons. (T5)
10. What is `info` for in HKDF? (T5)
11. A function passes "deterministic", "right length", "salt changes it" and "info changes it".
    What does that prove? What test would prove more? (T6)
12. JDK 26 has ML-KEM. Does a TLS connection from JDK 26 use it? How would you check? (T7)

**Done when:** you can answer all twelve without looking.
