# M05 — Discovering modules

A tour of the read-only command-line tools that tell you *what is on the
module path right now*. These are the everyday tools for diagnosing "module X
is missing" or "why am I leaking class Y".

## Section 1: `java --list-modules`

- Lists every module visible to the launcher: every JDK module plus every JAR
  on `--module-path`.
- With no arguments it lists JDK modules only. Add `-p mods` to see your own
  modules in the list.
- Useful first step when triaging a missing-module error: confirm the JAR is
  actually being picked up.

## Section 2: `java --describe-module <name>`

- Prints the module's `module-info` summary: requires, exports, opens, uses,
  provides — plus a `[automatic]` tag for automatic modules.
- The `(qualified)` annotation marks qualified `exports … to` lines.
- Combine with `-p` to describe one of your own modules.

## Section 3: `java --show-module-resolution`

- Adds verbose tracing of the module graph that the launcher resolves at
  startup: which module pulled in which other module, and from where.
- Indispensable for diagnosing `FindException: Module X not found`. The
  output shows the *first* missing edge.

## Section 4: `jdeps`

- Off-line analyser. Run on a JAR to see which packages it depends on, which
  modules contain those packages, and whether the dependency is internal
  (`jdk.internal.*` — discouraged).
- `jdeps --generate-module-info <out-dir> some-legacy.jar` scaffolds a
  proposed `module-info.java` for a legacy library — useful when migrating a
  pre-Java-9 dependency to a named module.

## Section 5: Built-in JDK modules worth knowing

- `java.base` — implicitly required by everyone; collections, IO, concurrency,
  the basics. Never declare `requires java.base` explicitly; it is already
  there.
- `java.sql` — JDBC.  `java.logging` — `java.util.logging`.
- `java.net.http` — the `HttpClient`.  `java.xml` — DOM/SAX/StAX.
- `jdk.*` modules expose JDK-specific tooling APIs (`jdk.compiler`,
  `jdk.jfr`, `jdk.unsupported`). Depend on them only when you really must.
