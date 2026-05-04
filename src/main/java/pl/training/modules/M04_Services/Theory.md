# M04 — Services with `uses` / `provides` and `ServiceLoader`

A four-module setup where the consumer never names the providers — it only
knows the interface. New providers can be plugged in at deploy time by
dropping a JAR onto the module path.

## Section 1: What is a service

- A *service* is a contract: an interface (the **service provider interface**,
  or SPI) plus any types it references.
- An *implementation* is a class that fulfils the contract.
- A *consumer* asks the runtime for "all implementations of this interface
  that are visible on the module path", without naming them.
- Services let independent teams ship modules that compose at runtime — the
  classic plugin pattern.

## Section 2: `provides X with Y`

- A producer module declares
  `provides zoo.tours.api.Tour with zoo.tours.guide.QuietTour;`.
- One module can list multiple `with` classes if it ships several
  implementations.
- The implementation class must have either a public no-arg constructor or a
  public static `provider()` method that returns an instance.

## Section 3: `uses X`

- The consumer module declares `uses zoo.tours.api.Tour;` to announce it will
  ask `ServiceLoader` for implementations of that interface.
- Without `uses`, `ServiceLoader.load(...)` returns nothing — the runtime
  refuses to surface implementations to a module that did not opt in.

## Section 4: `ServiceLoader.load(Class<S>)`

- Returns an iterable of every implementation discovered on the module path.
- Lazy: each implementation is instantiated on first access during iteration,
  not up front.
- The iteration order is not specified — your code should not depend on it.
- For a "first one wins" pattern call `findFirst()`; to enumerate every
  implementation use `stream()` or a foreach.

## Section 5: Why services

- Decouples the consumer from a specific implementation. Adding or removing a
  provider is a deploy-time change, not a code change.
- Standard JDK example: `java.sql.Driver` — drop a JDBC driver JAR on the
  module path and the JDK finds it through the same mechanism.
