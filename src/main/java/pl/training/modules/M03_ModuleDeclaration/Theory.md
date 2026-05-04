# M03 — Module declaration directives

Three directives that every JPMS user must know — beyond plain `exports` /
`requires`. The example deliberately includes one consumer that the build
*expects to fail*, to prove that strong encapsulation actually rejects bad
code.

## Section 1: Qualified `exports … to`

- `exports content to zoo.staff;` — the package is visible **only** to the
  listed module(s). Every other module that requires the producer still gets
  the unqualified exports but cannot see the qualified one.
- Use it for "friend package" patterns: an internal package shared between a
  small group of cooperating modules without leaking to the wider world.
- Multiple module names are allowed:
  `exports content to zoo.staff, zoo.kitchen;`.

## Section 2: `requires transitive`

- Plain `requires X` makes X visible **inside this module only**. Modules that
  require *this* module do not automatically read X.
- `requires transitive X` re-exports the dependency. Anyone requiring this
  module also reads X for free.
- Use case: a *facade* module whose public API mentions types from another
  module in method signatures. Without `transitive`, every consumer would
  have to add a duplicate `requires` to be able to call those methods.

## Section 3: `opens packageName [to …]`

- `opens` keeps a package encapsulated at compile time but allows
  *deep reflection* at run time. Frameworks like Hibernate, Jackson, and many
  DI containers need this to read private fields and call private
  constructors.
- `opens X to Y;` is the qualified form, restricting who can reflect on X.
- For a module where every package needs reflection access, declare the
  module itself as `open module Foo { ... }` — a nuclear option, prefer
  per-package `opens` in production code.

## Section 4: Build-time enforcement

- The producer exports `content` only to `zoo.staff`. A consumer that does
  not match the qualified-export list but tries to import a class from
  `content` will fail to compile with
  `error: package zoo.animal.talks.content is not visible`.
- The `run.sh` deliberately runs that bad compile to demonstrate the
  enforcement, captures its non-zero exit, prints "(this failure is
  expected)", and continues with the successful path.
