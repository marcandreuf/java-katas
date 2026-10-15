# 32. Crypto: key derivation, post-quantum, PEM

**Java 21 → 26** · stage 21 of [the release-ordered track](INDEX.md) · **needs `--release 25` or newer**

## Where it lands

New code: **`crypto/Derive.java`**, four TODOs. Nothing in this repo does any cryptography, so
there is nothing to modernise — but all three APIs below are present and working on the JDK in
this VM, which makes this a real exercise rather than reading.

## 1. The KDF API (Java 25, JEP 510)

    KDF hkdf = KDF.getInstance("HKDF-SHA256");
    var params = HKDFParameterSpec.ofExtract()
            .addIKM(new SecretKeySpec(ikm, "RAW"))
            .addSalt(salt)
            .thenExpand(info, 32);
    byte[] key = hkdf.deriveData(params);

**Before Java 25 there was no KDF abstraction at all.** HKDF is specified in RFC 5869 and
everyone implemented it out of `Mac` by hand — extract, then expand in `ceil(L/HashLen)` blocks
with a counter — and a meaningful proportion of those implementations were wrong in ways that
still produced plausible-looking bytes.

Three properties the test pins, and each is the point of a parameter:

- **deterministic** — same inputs, same key, on every machine. A KDF is a function, not a
  generator. If you were tempted to reach for `SecureRandom` here, that is the distinction.
- **salt changes the key** — the extract step
- **`info` changes the key** — *domain separation.* One shared secret, many purposes, different
  key for each, and you must not reuse one key for encryption and authentication. The `info`
  parameter is how you say which purpose this key is for, and it is the parameter people omit.

## 2. Post-quantum: ML-KEM and ML-DSA (Java 24, JEP 496 and 497)

    KeyPairGenerator.getInstance("ML-KEM").generateKeyPair();   // key encapsulation
    KeyPairGenerator.getInstance("ML-DSA").generateKeyPair();   // signatures

These are the NIST-standardised lattice schemes, formerly Kyber and Dilithium. Measured on this
JDK:

| | Encoded public key |
| --- | --- |
| ML-KEM | **1206 bytes** |
| ML-DSA | **1974 bytes** |
| EC P-256, for comparison | ~91 bytes |

**That table is the entire practical story of post-quantum cryptography.** The maths is somebody
else's problem; the engineering problem is that keys and signatures got one to two orders of
magnitude bigger, and everything with a size assumption in it — TLS handshake round trips,
certificate chains, JWT headers, QR codes, embedded flash budgets — has to absorb that.

**Why now, when there is no quantum computer:** *store now, decrypt later.* An adversary records
your TLS traffic today and decrypts it in 2040. For anything that must stay secret for fifteen
years, the migration deadline has already passed. Signatures are less urgent, because a signature
verified today cannot be retroactively forged.

### TODO 3 and what a KEM is not

    var enc = kem.newEncapsulator(publicKey).encapsulate();
    var dec = kem.newDecapsulator(privateKey).decapsulate(enc.encapsulation());
    // enc.key() and dec agree -- verified

A KEM does **not** encrypt your message. It gives both sides the same random symmetric key, plus
a blob to send over the wire. You then encrypt with that key using AES-GCM — and derive the actual
encryption key from it with the HKDF in TODO 1, which is why these two APIs are in one exercise.

This is also the shape RSA key transport always had; the KEM API (Java 21, JEP 452) just named it,
which is what let ML-KEM slot in three releases later without a new interface.

## 3. What is *not* here yet

    SSLContext.getDefault().getSupportedSSLParameters().getNamedGroups()

On the JDK 25 in this VM:

    [x25519, secp256r1, secp384r1, secp521r1, x448, ffdhe2048, ... ]

**All classical. No hybrid group.** So you can generate ML-KEM keys on Java 24+ and your TLS
connections are still entirely classical, because nothing wires the two together.

That is what **JEP 527 (Java 27)** does: it adds `X25519MLKEM768` and offers it *first* in TLS 1.3.
It is a **hybrid** — classical X25519 **and** ML-KEM together, so the connection is safe unless
both are broken. Nobody is confident enough in lattices alone to drop the classical half.

This track stops at Java 26, so JEP 527 is noted and not taught. **The useful thing is the gap
itself**: an algorithm shipping in the JDK and that algorithm being used by default are three
releases apart, and `getNamedGroups()` is how you tell which side of that gap you are on.

**PEM encodings** (JEP 470 in 25, 524 in 26) are on their second preview and still not final — the
`PEMEncoder`/`PEMDecoder` pair that finally makes `-----BEGIN PRIVATE KEY-----` a JDK concern
rather than a Bouncy Castle one. [Stage 22](33-preview-shelf.md) covers it as a preview.

## Write the answers here

1. **Measure the key sizes yourself** and add RSA-2048 and EC P-256 to the table. _(fill in)_
2. **`info` is domain separation.** Describe a concrete bug caused by omitting it. _(fill in)_
3. **Your own "store now, decrypt later" audit.** Name one thing your systems transmit that must
   stay secret for ten years, and say whether it is protected today. _(fill in)_
4. **Why hybrid rather than ML-KEM alone**, when ML-KEM is the NIST standard? _(fill in)_
5. **Run `getNamedGroups()` on a 27 JDK** (`sdk use java 27.0.0.0+35-tem`) and record the
   difference. _(fill in)_

## What got worse

- _(fill this in)_

Prompts: a 1206-byte public key in a TLS ClientHello — what does that do to the number of round
trips on a lossy mobile link? If a KEM only gives you a shared secret, how many ways are there to
get the next step wrong, and does the JDK help with any of them? Is "the JDK now has HKDF" a
reason to stop using a crypto library, or a reason to read its source?
