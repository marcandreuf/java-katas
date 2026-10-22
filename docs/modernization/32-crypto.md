# 32. Crypto: key derivation, post-quantum, PEM

**Java 21** (KEM API, JEP 452) · **Java 24** (ML-KEM and ML-DSA, JEPs 496 and 497) · **Java 25**
(KDF API final, JEP 510) · stage 21 of [the release-ordered track](INDEX.md) · no build change:
the repo already compiles at `--release 26`

New code: **`crypto/Derive.java`**, four TODOs, and `DeriveTest`, seven tests behind one
`@Disabled`. Nothing else in the repo does cryptography, so there's nothing to modernise and no
characterization step. The first commit is a missing test instead (part one).

## What this stage is actually about

Five words, defined once:

- a **KDF** (key derivation function) turns one secret into one or more keys. **HKDF**
  (RFC 5869) does it in two steps: **extract** mixes the input with a **salt** into a fixed-size
  key, and **expand** stretches that to the length you ask for, bound to an **info** string.
- **domain separation** is what `info` is for: the same secret, a different purpose, a different
  key. You must not use one key for both encryption and authentication.
- a **KEM** (key encapsulation mechanism) gives two parties the same random symmetric key. One
  side **encapsulates** with the other's public key and gets the key plus a blob to send. The
  other side **decapsulates** the blob with its private key and gets the same key. **It doesn't
  encrypt your message.**
- **ML-KEM** and **ML-DSA** are the NIST post-quantum standards (formerly Kyber and Dilithium),
  for key exchange and for signatures. **Post-quantum** means believed safe against a large
  quantum computer, which would break RSA and elliptic curves.
- a **test vector** is a published input with its known correct output. It's the only kind of
  test that can tell a correct crypto implementation from a plausible-looking wrong one.

That gives one rule, and everything below follows from it: **crypto that's wrong still produces
bytes that look fine, so a test has to compare against a known answer.** "Same input gives the
same output" and "different input gives different output" are true of almost any function. They
don't tell you it's the right function.

## The worked example: a signature, three ways

Not one of the TODOs: sign `"hello"`, verify it, and verify a tampered copy (verified, JDK 26,
default parameters):

| Algorithm | public key (encoded) | signature | verify | tampered |
| --- | --- | --- | --- | --- |
| `Ed25519` | 44 bytes | 64 bytes | true | false |
| `SHA256withECDSA`, P-256 | 91 bytes | ~71 bytes | true | false |
| `SHA256withRSA`, 3072-bit | 422 bytes | 384 bytes | true | false |
| **`ML-DSA`** (ML-DSA-65) | **1974 bytes** | **3309 bytes** | true | false |

The code is the same four lines for all four: `Signature.getInstance(alg)`, `initSign`, `update`,
`sign`. **That's the JCA design paying off**: ML-DSA slotted in behind an interface from 1997.
The engineering problem is the last row. Keys and signatures got one to two orders of magnitude
bigger, and everything with a size assumption has to absorb that: TLS handshakes, certificate
chains, JWT headers, QR codes, embedded flash budgets.

Two defaults worth knowing, both verified: on JDK 26 `KeyPairGenerator.getInstance("EC")` gives
**P-384** (120-byte key), not P-256, and `"RSA"` gives **3072** bits. If you're filling in a size
table, set the parameters explicitly.

**Why now, when there is no quantum computer:** *store now, decrypt later.* Someone records your
TLS traffic today and decrypts it in 2040. Anything that has to stay secret for fifteen years is
already late. Signatures are less urgent: a signature verified today can't be forged
retroactively.

## Part one: HKDF, and the test that's missing

`DeriveTest` has four HKDF tests: the requested length, determinism, salt changes the key, info
changes the key. **A correction to the old version:** it said those "pin" HKDF. They don't. This
is not HKDF:

    static byte[] hkdf(byte[] ikm, byte[] salt, byte[] info, int len) {    // NOT HKDF
        return Arrays.copyOf(sha256(ikm, salt, info), len);
    }

and it **passes all four** (verified). It's deterministic, it gives the length you ask for (up to
32), and it changes when the salt or the info changes. It's also wrong for every input.

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
3. **Then question 2**: describe a real bug caused by leaving `info` empty.

Before Java 25 there was no KDF abstraction. It was previewed in 24 (JEP 478) and final in 25
(JEP 510). Everyone built HKDF out of `Mac` by hand, and the test above is how you'd have
found out whether yours was right.

## Part two: ML-KEM, TODOs 2 and 3

TODO 2 is the key pair. The test asks for an encoded public key over 1000 bytes. Verified on JDK
26: `ML-KEM` defaults to **ML-KEM-768**, with a **1206-byte** public key, and the encapsulation
blob is **1088 bytes**.

TODO 3 is the round trip. What the encapsulator gives you is a `SecretKey` of algorithm
`Generic`, 32 bytes (verified). **Write down what you would do with it next** (the TODO asks
this). Hint: it isn't "use it as the AES key directly", and part one has the answer. That's why
these two APIs are in one exercise.

The KEM API itself is Java 21 (JEP 452), three releases before ML-KEM. RSA key transport always
had this shape. JEP 452 named it, and that's what let ML-KEM arrive in 24 without a new interface.

## Part three: what TLS actually uses, TODO 4

    SSLContext.getDefault().getSupportedSSLParameters().getNamedGroups()

On JDK 26 (verified):

    [x25519, secp256r1, secp384r1, secp521r1, x448, ffdhe2048, ffdhe3072, ffdhe4096, ffdhe6144, ffdhe8192]

**All classical.** JDK 26 can generate ML-KEM keys, and its TLS connections are entirely
classical, because nothing wires the two together. Asking for a hybrid group doesn't help:
`-Djdk.tls.namedGroups=X25519MLKEM768,x25519` leaves only `[x25519]` (verified). The unknown
name is dropped silently.

The INDEX says Java 27 adds `X25519MLKEM768` (JEP 527), a **hybrid** of classical X25519 and
ML-KEM, safe unless both are broken. There's no JDK 27 on this machine, so that's unverified
here. **The useful thing is the gap itself**: an algorithm shipping in the JDK and that algorithm
being used by default are releases apart, and `getNamedGroups()` is how you tell which side of the
gap you're on.

**PEM encodings** (JEP 470 in 25, JEP 524 in 26) are still preview on 26. Without the flag,
`PEMEncoder is a preview API and is disabled by default`. With `--enable-preview`,
`java.security.PEMEncoder.of()` works (both verified). [Doc 33](33-preview-shelf.md) is about
reading that signal.

## Write the answers here

1. **The size table, measured yourself.** Add the encoded private key sizes, and say why the
   ML-KEM private key is so much smaller than its public key on this JDK. (Look at what it
   encodes.) _(fill in)_
2. **`info` is domain separation.** Describe a concrete bug caused by omitting it. _(fill in)_
3. **Your own "store now, decrypt later" audit.** Name one thing your systems transmit that must
   stay secret for ten years, and say whether it's protected today. _(fill in)_
4. **Why hybrid rather than ML-KEM alone**, when ML-KEM is the NIST standard? _(fill in)_
5. **Your four HKDF tests passed a function that wasn't HKDF.** Name another place, in code you
   work on, where the tests check properties and never a known answer. _(fill in)_

## What got worse

- _(fill this in)_

Prompts: a 1206-byte public key in a TLS ClientHello: what does that do on a lossy mobile link?
A KEM only gives you a shared secret, so how many ways are there to get the next step wrong, and
does the JDK help with any of them? An unknown TLS group name is dropped without a warning: is
that the right behaviour for a security setting? Is "the JDK now has HKDF" a reason to stop using
a crypto library, or a reason to read its source?
