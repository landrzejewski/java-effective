# M01 — Hello Module

The smallest possible JPMS round-trip: declare one named module, build a JAR
for it, and run its `main` from the module path.

## Section 1: What is a module

- A *module* is a JAR with one extra file at its root: `module-info.class` (compiled
  from `module-info.java`).
- The module declaration spells out three things: the module's name, which
  packages it exports, and which other modules it requires.
- On the **module path** (`-p` / `--module-path`), JARs are loaded as modules
  and only see what their `module-info` says they can see.
- On the **classpath** (`-cp`), JARs are loaded the legacy way: every public
  type in every package is visible to everyone. JPMS exists because that
  default is the source of "JAR hell".

## Section 2: The `module-info.java` file

- Lives at the **module root**, next to (not inside) the package directories.
- Uses the `module` keyword, not `class` or `interface`:
  ```java
  module zoo.animal.feeding {
  }
  ```
- The module name follows package-name rules — dots OK, dashes not. By
  convention it matches the root package: `zoo.animal.feeding` for a module
  whose top package is `zoo.animal.feeding`.
- A bare `module Foo {}` declaration is enough to *run* the module, but it
  exports nothing — other modules cannot read its packages until you add
  `exports` (covered in M02).

## Section 3: module-path vs class-path

- `--module-path` (short `-p`) introduces a directory or JAR list searched for
  *modules*. Every JAR in there with a `module-info.class` becomes a named
  module; JARs without one become **automatic modules** (covered in M06).
- `--module <name>/<MainClass>` (short `-m`) launches by module name and main
  class. The legacy `-cp X.jar Main` style still works for non-modular code.
- Mixing the two on one command line is allowed but rarely useful — keep
  modular code on `-p` and only fall back to `-cp` for legacy libraries.

## Section 4: The build cycle

The four-line recipe used by every later topic in this directory:

```
javac -d <out>  --module-source-path src  -m <moduleName>     # compile
jar  --create   --file mods/<module>.jar  --module-version 1  # package
     -C <out>/<moduleName> .
java -p mods    -m <moduleName>/<MainClass>                   # run
```

The `<out>` directory holds class files in a per-module subdirectory; the
final `mods/` directory is what `java -p` reads at run time.
