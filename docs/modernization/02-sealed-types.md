# 02. Sealed types and exhaustiveness

## Where it lands

Nowhere in the old code, which is itself worth noticing. Nothing in this repo has a type hierarchy
with variants that carry different data, so there is nothing to seal. That is why this one
exercise is new code: `sealedtypes/ToolCallResult.java`.

## The exercise

Work the four TODOs in order. **TODO 4 is the one that matters**: add a fifth variant, watch the
compiler point at `route()`, then take it out again. That compile error is the thing sealed types
buy you, and it is the answer to give when someone asks why you used one.

## Then the counter-example

Go back to `Door`. It has two states, so `sealed interface DoorState permits Open, Closed` will
compile and will be worse than the boolean it replaces, and worse than an enum.

**Write down where the line is.** Sealed types earn their keep when variants carry different data.
When they carry none, an enum already does the exhaustiveness check and costs two lines instead of
twenty. Being able to say that is more useful than being able to write the sealed interface.

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: what does sealing cost when the variants genuinely belong to different modules? What
happens to a library's ability to extend your type, and when is that a feature?
