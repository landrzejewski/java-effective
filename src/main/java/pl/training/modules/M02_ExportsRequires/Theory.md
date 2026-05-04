# M02 — exports + requires

Two named modules: a `feeding` library that exports a single package, and a
`care` application that requires the library and calls into it.

## Section 1: `exports packageName`

- A module exposes a package to the outside world only when it explicitly
  `exports` it. Without that line, even `public` types in the package are
  unreachable from other modules.
- `exports` is the encapsulation control. Internal helper packages stay
  private; only the API surface is published.
- Exporting a package does **not** make all its classes public — Java's normal
  access modifiers still apply. `exports` plus `public` is the combination
  that crosses the module boundary.

## Section 2: `requires moduleName`

- `requires X` is both a compile-time and run-time dependency declaration. The
  compiler refuses to compile if `X` is missing on the module path; the
  launcher refuses to start.
- The dependency is **directed**: A `requires` B does not imply B `requires` A.
- Cycles between modules are forbidden. If two modules genuinely depend on
  each other, the right answer is to extract a third module they both depend
  on.
- Transitive consumers (B requires A, C requires B) do **not** automatically
  get to read A. Use `requires transitive` (covered in M03) to forward the
  dependency.

## Section 3: Strong encapsulation

- Even reflection cannot reach a non-exported package by default. A library
  cannot probe internal types of another module unless that module
  *deliberately* exposes them with `exports` or `opens` (M03).
- This is qualitatively stronger than the classpath world, where any code
  could `Class.forName("internal.Thing")` and access anything via
  `setAccessible(true)`.

## Section 4: The build

- Each module is compiled into its own per-module subdirectory, then jarred
  into a single `mods/` directory.
- The consumer module's `javac` invocation passes `-p mods` so the compiler
  can resolve `requires zoo.animal.feeding`.
- A single `java -p mods -m zoo.animal.care/...` line launches the whole
  graph: the launcher reads the consumer's `module-info`, resolves its
  dependencies, and starts the right `main`.
