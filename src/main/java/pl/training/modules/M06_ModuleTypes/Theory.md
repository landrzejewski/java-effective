# M06 — Named, automatic, and unnamed modules

The same `zoo.legacy` JAR is consumed two different ways, demonstrating that
the very same artefact behaves as a different *kind* of module depending on
how the launcher loads it.

## Section 1: Named modules

- A named module has a `module-info.class` at its root, lives on the
  `--module-path`, and is fully encapsulated.
- This is the modern target. Any new code should declare a module-info.

## Section 2: Automatic modules

- A plain JAR (no `module-info.class`) placed on `--module-path` becomes an
  **automatic module**.
- Its name is derived from the filename — `widget-toolkit-1.2.jar` →
  `widget.toolkit`. The owner can override this by adding
  `Automatic-Module-Name: <name>` in `META-INF/MANIFEST.MF`.
- An automatic module exports every package and reads every other module on
  the path — encapsulation is effectively off.
- Used as a stepping-stone during migration: a legacy library that is not
  yet modularised can be required by a named module *as if* it were one.

## Section 3: Unnamed modules

- Any class loaded from the **classpath** belongs to the *unnamed module*.
- Code in the unnamed module can read every other module (including named
  ones), but **named modules cannot require the unnamed module** — they have
  no name to refer to.
- This is what classpath-only legacy code becomes when JPMS is involved.

## Section 4: Migration ladder

1. Start with everything on the classpath (unnamed).
2. Move a leaf JAR to `--module-path` — it becomes an automatic module. Other
   classpath code keeps working unchanged.
3. Add a `module-info.java` to that leaf JAR. It is now a named module with
   strong encapsulation.
4. Repeat upward. The application code is the *last* thing to become modular.

This bottom-up approach lets a large codebase migrate one library at a time
without a flag-day rewrite.
