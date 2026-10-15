# 24. The Class-File API

**Java 24** · stage 13 of [the release-ordered track](INDEX.md) · **needs `--release 24` or newer**

## Where it lands

New code: **`classfile/Inspect.java`**, four TODOs. It parses `Door.class` — a file this repo
compiles anyway — so there is nothing to generate and nothing to download.

This is the shallowest stage in the track on purpose. You are not going to write a bytecode
rewriter. The point is to know what the API is for and why it shipped.

## Why it exists

Every tool that read or wrote bytecode shipped a copy of **ASM**: Spring, Hibernate, Mockito,
Jacoco, Lombok, every agent, every mocking framework. ASM is good, and the arrangement had one
structural flaw: **ASM has to be updated for each new class file format version, and it is not
part of the JDK.**

So the sequence each September was: new Java release, class file version goes up, every
ASM-dependent library throws `UnsupportedClassVersionError` or
`IllegalArgumentException` from deep inside a framework, and the whole ecosystem waits for ASM,
then for each library to bump it. **If you have ever been unable to upgrade Java because Mockito
could not read the new bytecode, this JEP is the fix** — the API is maintained in the JDK, in
lockstep with the format, and cannot lag it.

That is the answer worth having. The API surface is a detail.

## The exercise

    ClassModel cm = ClassFile.of().parse(bytes);
    cm.majorVersion();                                              // 65 = Java 21
    cm.methods().stream().map(m -> m.methodName().stringValue());
    cm.fields().stream().map(f -> f.fieldName().stringValue());

**TODO 1** is the one with something to learn: get a class's own bytes off the classpath. The
resource name of a class is not its fully qualified name, and `Class.getResourceAsStream`
resolves relative to the class's own package, so `Door.class.getResourceAsStream("Door.class")`
works and is shorter than anything you were about to write.

**TODO 2**, the version number. **major = release + 44**:

| Java | 8 | 11 | 17 | 21 | 24 | 25 | 26 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| major | 52 | 55 | 61 | 65 | 68 | 69 | 70 |

This is the table behind the most-seen error message in Java:

    UnsupportedClassVersionError: ... has been compiled by a more recent version of the Java
    Runtime (class file version 69.0), this version of the Java Runtime only recognizes
    class file versions up to 65.0

**69 means someone compiled on 25 and ran on 21.** Being able to decode that on sight, without
looking it up, is worth more day to day than the rest of this document. The test asserts
`>= 65` rather than an exact number, because the number now tracks `<java.release>` — which is 26
in this repo, so the test sees 70.

**TODO 3** has a small surprise in it. `Door.class` reports four methods:

    [<init>, isOpen, toggle, toString]

`Door.java` declares three. **`<init>` is the constructor**, under a name that is not a legal Java
identifier precisely so that it cannot collide with a method. `<clinit>` is the static initialiser
and appears the moment you add a `static final` field with a computed value. Seeing those two in a
list is the cheapest possible introduction to the idea that the class file is not the source file.

## What got worse

- _(fill this in)_

Prompts: this API went final two releases after it previewed and ASM has existed since 2002 —
what does the JDK gain by owning it that a well-maintained library could not provide? If you were
writing an agent today, would you switch, and what would decide it? The API is immutable and
builder-based where ASM is a visitor — which style would you rather debug?
