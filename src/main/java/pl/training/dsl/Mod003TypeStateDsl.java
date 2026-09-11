package pl.training.dsl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/*
The "builder forgot a required field" bug

- A classic builder lets you call build() no matter which fields were set. The check therefore lives inside
  build() and fires at run time, which means the caller learns about the mistake from a stack trace — possibly
  in production, possibly on a path the tests never took.
- Type-state moves that check into the type system: the terminal step is only applicable to a builder whose type
  says every required field is present. Forgetting one becomes a compile error.
- Two encodings are shown here. Phantom type parameters (§1-§3) scale to several independent required fields.
  Staged interfaces (§4) are simpler to read and are what most Java code actually uses. Both are common: AWS SDK
  v2 and OkHttp use runtime checks, while jOOQ's SELECT ... FROM ... chain is staged interfaces.

Phantom types

- A phantom type parameter appears in the type of a value but nowhere in its runtime state. It carries no data;
  it exists only so the compiler can tell two otherwise identical values apart. After erasure there is exactly
  one HttpRequestBuilder class, which §2 demonstrates.
- The markers are a sealed interface with two implementations that no caller can instantiate. Sealing stops a
  caller from inventing a third state; the private constructors stop anyone OUTSIDE this file from creating a
  marker value, which nothing needs, because the markers are only ever used as types. (Inside this file the
  compiler would allow `new Missing()`: private is scoped to the enclosing top-level class, JLS 6.6.1.)
*/

public final class Mod003TypeStateDsl {

    private Mod003TypeStateDsl() {}

    // =================================================================================================
    // Markers — types with no values
    // =================================================================================================

    public sealed interface State permits Missing, Present {}
    public static final class Missing implements State { private Missing() {} }
    public static final class Present implements State { private Present() {} }

    // =================================================================================================
    // The thing being built
    // =================================================================================================

    public record HttpRequest(String method, String url, Map<String, String> headers, String body) {

        /*
        Defensive copy in a compact constructor, so the record cannot be handed a map somebody else still
        holds a reference to. Note that it is *not* Map.copyOf: that returns an unordered map, which would
        shuffle the headers. Preserving the order needs a LinkedHashMap wrapped as unmodifiable.
        */
        public HttpRequest {
            headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        }

        public String render() {
            var out = new StringBuilder(method).append(' ').append(url).append('\n');
            headers.forEach((name, value) -> out.append(name).append(": ").append(value).append('\n'));
            if (body != null && !body.isEmpty()) out.append('\n').append(body);
            return out.toString();
        }
    }

    // =================================================================================================
    // Encoding 1 — phantom type parameters, one per required field
    // =================================================================================================

    public static final class HttpRequestBuilder<U extends State, M extends State> {
        private final String url;
        private final String method;
        private final Map<String, String> headers;
        private final String body;

        private HttpRequestBuilder(String url, String method, Map<String, String> headers, String body) {
            this.url = url; this.method = method; this.headers = headers; this.body = body;
        }

        // Setting a required field flips its parameter to Present and leaves the other one alone.
        // No cast is needed: U and M appear in no field and in no constructor parameter, so the diamond
        // takes its parameterisation from the declared return type.
        public HttpRequestBuilder<Present, M> url(String url) {
            return new HttpRequestBuilder<>(url, method, headers, body);
        }
        public HttpRequestBuilder<U, Present> method(String method) {
            return new HttpRequestBuilder<>(url, method, headers, body);
        }

        // Optional fields preserve both parameters — they neither satisfy nor invalidate a requirement.
        public HttpRequestBuilder<U, M> header(String name, String value) {
            var copy = new LinkedHashMap<>(headers);
            copy.put(name, value);
            return new HttpRequestBuilder<>(url, method, copy, body);
        }
        public HttpRequestBuilder<U, M> body(String body) {
            return new HttpRequestBuilder<>(url, method, headers, body);
        }

        // Named shortcuts satisfy the same requirement as method(...) and say so in their return type.
        public HttpRequestBuilder<U, Present> get()  { return method("GET"); }
        public HttpRequestBuilder<U, Present> post() { return method("POST"); }
    }

    /** Entry point: the only door into the chain, and it starts with nothing set. */
    public static HttpRequestBuilder<Missing, Missing> request() {
        return new HttpRequestBuilder<>(null, null, new LinkedHashMap<>(), null);
    }

    /*
    The terminal step is a free static function, not an instance method, and that is forced on us: Java cannot
    make an instance method's existence depend on how `this` is parameterised. There is no way to write
    "declare send() only when U = Present and M = Present" — a method is either on the class or it is not.
    Putting the requirement in a parameter type achieves the same rejection, at the cost of the chain no longer
    ending in a dot: you read send(request()...) inside-out. §4 shows the encoding that keeps the dot.
    */
    public static HttpRequest send(HttpRequestBuilder<Present, Present> builder) {
        return new HttpRequest(builder.method, builder.url, builder.headers, builder.body);
    }

    // =================================================================================================
    // Sections
    // =================================================================================================

    /*
    The type advances call by call

    - request() hands back <Missing, Missing>. Each required setter flips exactly one parameter, so the type of
      the expression records how much of the request has been supplied.
    - Because each step returns a new value, a half-configured builder is a legitimate thing to keep and reuse —
      a common base with the URL set, say, from which several requests are derived.
    */
    static void typeAdvancesPerCall() {
        System.out.println("[Section 1] the type advances with each call");

        var b0 = request();                 // HttpRequestBuilder<Missing, Missing>
        var b1 = b0.url("https://x");       // HttpRequestBuilder<Present, Missing>
        var b2 = b1.get();                  // HttpRequestBuilder<Present, Present>

        System.out.println("  b0: nothing set yet");
        System.out.println("  b1: url set, method still missing");
        System.out.println("  b2: both set — send() now accepts it");
        System.out.println("  send(b2) -> " + send(b2).render().lines().findFirst().orElseThrow());
    }

    /*
    Erasure — the parameters exist only at compile time

    - Missing and Present never reach the bytecode. b0 and b2 have the same class and the same fields; only the
      compiler ever saw a difference between them.
    - That also names the limit of the technique. A raw type erases the parameterisation and a cast invents it
      back, so send((HttpRequestBuilder<Present, Present>) (HttpRequestBuilder) request()) compiles — with an
      unchecked warning — and then blows up at run time. Type-state prevents mistakes, not sabotage.
    - If a value can arrive from outside your compilation unit (deserialised, reflected, or built by code that
      ignores warnings), keep a cheap runtime check in the terminal step as well.
    */
    static void erasure() {
        System.out.println("[Section 2] the phantom parameters are erased");

        var b0 = request();
        var b2 = b0.url("https://x").get();

        System.out.println("  same runtime class? " + (b0.getClass() == b2.getClass()));
        System.out.println("  class = " + b0.getClass().getSimpleName() + " (no type argument survives)");
    }

    /*
    What the compiler rejects

    - The three lines below are exactly the mistakes a runtime-checked builder would let through:
        send(request());                    url and method missing
        send(request().url("https://x"));   method missing
        send(request().get());              url missing
    - Each is rejected because Missing and Present are unrelated types and generics are invariant:
      HttpRequestBuilder<A, B> is never a subtype of HttpRequestBuilder<C, D> unless the arguments are equal.
    - The error names the type-state, for instance
        incompatible types: HttpRequestBuilder<Missing,Present> cannot be converted to
                            HttpRequestBuilder<Present,Present>
      which tells the reader that the URL is the part still missing.
    */
    static void whatIsRejected() {
        System.out.println("[Section 3] what does not compile");
        System.out.println("  send(request())                  // url and method missing");
        System.out.println("  send(request().url(\"https://x\")) // method missing");
        System.out.println("  send(request().get())            // url missing");
        System.out.println("  each error names the offending type argument, so it points at the missing call");
    }

    /*
    Encoding 2 — staged interfaces

    - Instead of one class with several parameters, use one interface per stage and let each stage expose only
      the calls that are legal next. The terminal method lives on the last stage, so the chain ends in a dot and
      reads left to right.
    - Trade-off. Staged interfaces are far easier to read, both in the source and in error messages, and they
      also enforce an *order*. But that is the catch: the stages are a straight line, so this encoding cannot
      express "two required fields, in either order". Phantom parameters can, which is the whole reason they
      exist. Pick staged interfaces when the order is natural, phantom parameters when it is not.
    - One implementation class can implement every stage; the interfaces exist purely to narrow what is visible.
    */
    public interface NeedsUrl    { NeedsMethod url(String url); }
    public interface NeedsMethod { Ready method(String method); default Ready get() { return method("GET"); } }
    public interface Ready       { Ready header(String name, String value); HttpRequest send(); }

    private static final class StagedBuilder implements NeedsUrl, NeedsMethod, Ready {
        private String url, method;
        private final Map<String, String> headers = new LinkedHashMap<>();

        @Override public NeedsMethod url(String url)       { this.url = url; return this; }
        @Override public Ready method(String method)       { this.method = method; return this; }
        @Override public Ready header(String n, String v)  { headers.put(n, v); return this; }
        @Override public HttpRequest send()                { return new HttpRequest(method, url, headers, null); }
    }

    /** Entry point of the staged encoding: the caller only ever sees the interfaces. */
    public static NeedsUrl http() { return new StagedBuilder(); }

    static void stagedInterfaces() {
        System.out.println("[Section 4] staged interfaces — the chain keeps its dot");

        HttpRequest req = http()
                .url("https://api.example.com/users/42")
                .get()
                .header("Accept", "application/json")
                .send();

        System.out.println("  " + req.render().lines().findFirst().orElseThrow());
        System.out.println("  http().send()             // NeedsUrl has no send()");
        System.out.println("  http().url(\"...\").send() // NeedsMethod has no send() either");
    }

    /*
    End-to-end, with checks

    Build a GET and a POST through the phantom-typed encoding and compare the rendered text against a reference.
    Header order is preserved because the builder threads a LinkedHashMap and send() copies it in order.
    */
    static void endToEnd() {
        System.out.println("[Section 5] end-to-end with reference checks");

        var get = send(request()
                .url("https://api.example.com/users/42")
                .get()
                .header("Accept", "application/json"));

        var post = send(request()
                .url("https://api.example.com/users")
                .post()
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "abc123")
                .body("{\"name\":\"alice\"}"));

        String expectedGet = """
                GET https://api.example.com/users/42
                Accept: application/json""";
        String expectedPost = """
                POST https://api.example.com/users
                Content-Type: application/json
                Idempotency-Key: abc123

                {"name":"alice"}""";

        boolean getOk  = get.render().stripTrailing().equals(expectedGet);
        boolean postOk = post.render().stripTrailing().equals(expectedPost);

        System.out.println("  GET matches reference?  " + getOk);
        System.out.println("  POST matches reference? " + postOk);
        System.out.println("  POST rendered:");
        post.render().lines().forEach(line -> System.out.println("    " + line));
        System.out.println("  both checks pass? " + (getOk && postOk));
    }

    public static void main(String[] args) {
        typeAdvancesPerCall();
        erasure();
        whatIsRejected();
        stagedInterfaces();
        endToEnd();
        System.out.println("Mod003TypeStateDsl finished");
    }
}
