package pl.training.dsl;

import java.util.LinkedHashMap;
import java.util.Map;

// =================================================================================================
// Section 1: The "builder forgot a required field" bug
// =================================================================================================

/*
## The "builder forgot a required field" bug

- A classic Builder lets you call `.build()` regardless of which fields
have been set. Defensive checks in `build()` push the error to runtime
and force the user to write a fresh `try/catch` or read API docs to
remember which fields are required.
- A *type-state-safe* builder encodes the build progress in the type
system: `.build()` (or here, `.send()`) is **declared only on a fully
configured type parameterisation**. Forgetting a required field becomes a
compile error, not a runtime exception.
- This pattern is common in production SDKs: AWS SDK v2's request
builders, Mockito's `when(...)` chain, OkHttp's `Request.Builder` (in
spirit; the real one uses runtime checks but the idiom is identical).
*/

// =================================================================================================
// Section 2: Type-state pattern
// =================================================================================================

/*
## Type-state pattern

- Model "is the URL set yet?" and "is the method set yet?" with marker
types `Missing` and `Present`.
- The builder carries them as phantom type parameters
`HttpRequestBuilder<UrlState, MethodState>`.
- Setting a field returns a builder with the corresponding type parameter
flipped to `Present`.
- The runtime payload is unchanged; only the type tells the next reader
what is allowed.
*/

// =================================================================================================
// Section 3: Sealed marker interfaces
// =================================================================================================

/*
## Sealed marker interfaces

- `sealed interface State permits Missing, Present {}` declares the
universe of marker types. Sealing forbids users from inventing their own
`State` implementations and bypassing the safety.
- Each marker is an empty `final class`; it has no instances at all (we
never `new Present()`), it lives only in the type system.
- For larger DSLs you can have more states: `Empty`, `WithUrl`,
`WithUrlAndMethod`, ... — same pattern, more parameters.
*/

// =================================================================================================
// Section 4: The HTTP builder
// =================================================================================================

/*
## The HTTP builder

Required fields: URL, method.
Optional fields: headers, body.

```java
HttpRequest request = HttpRequest.builder()    // <Missing, Missing>
        .url("https://api.example.com/x")      // <Present, Missing>
        .method("POST")                        // <Present, Present>
        .header("Content-Type", "application/json")
        .body("{...}")
        .send();
```

The signature of each method tracks the build state advancing. `.send()`
is declared on `HttpRequestBuilder<Present, Present>` only.
*/

// =================================================================================================
// Section 5: What you cannot do
// =================================================================================================

/*
## What you cannot do

The lines below are intentionally rejected by the compiler. They are kept
as comments inside the example so the reader sees what would fail and why.

```java
HttpRequest.builder().send();                 // ✗ url & method missing
HttpRequest.builder().url("/x").send();       // ✗ method missing
HttpRequest.builder().method("GET").send();   // ✗ url missing
```

The compile errors talk about types ("method `send` not found in
`HttpRequestBuilder<Missing, Present>`"), not about runtime contracts.
That is the whole pay-off.
*/

// =================================================================================================
// Section 6: End-to-end
// =================================================================================================

/*
## End-to-end

- Build a `GET https://api.example.com/users/42`.
- Build a `POST https://api.example.com/users` with a JSON body and a
custom header.
- Run both through `.send()` (which here just renders the request as
text) and verify the rendered output against an expected reference.
*/

// =================================================================================================
// Section 7: Trade-off note
// =================================================================================================

/*
## Trade-off note

- Phantom-type builders make compile errors loud but add type-parameter
noise. The user has to read `HttpRequestBuilder<Present, Missing>` in
error messages.
- Worth it for SDKs and libraries used by hundreds of consumers; rarely
worth it for an in-app one-off builder. For one-offs, prefer a runtime
check with a clear exception message.
- Mid-ground: add a single phantom parameter for the most error-prone
field (e.g., `urlSet`) and keep the rest as runtime checks.
*/

public final class Mod008PhantomTypeBuilderDsl {

    private Mod008PhantomTypeBuilderDsl() {}

    // =================================================================================================
    // Marker types — sealed so users cannot invent new states
    // =================================================================================================

    public sealed interface State permits Missing, Present {}
    public static final class Missing implements State { private Missing() {} }
    public static final class Present implements State { private Present() {} }

    // =================================================================================================
    // The HttpRequest record (the thing built)
    // =================================================================================================

    public record HttpRequest(String url, String method,
                              Map<String, String> headers, String body) {

        public String render() {
            var sb = new StringBuilder();
            sb.append(method).append(' ').append(url).append('\n');
            for (var e : headers.entrySet()) sb.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
            if (body != null && !body.isEmpty()) sb.append('\n').append(body);
            return sb.toString();
        }
    }

    // =================================================================================================
    // The phantom-typed builder
    // =================================================================================================

    public static final class HttpRequestBuilder<U extends State, M extends State> {
        private final String url;
        private final String method;
        private final LinkedHashMap<String, String> headers;
        private final String body;

        private HttpRequestBuilder(String url, String method,
                                   LinkedHashMap<String, String> headers, String body) {
            this.url = url; this.method = method; this.headers = headers; this.body = body;
        }

        // Setting URL flips U: Missing -> Present
        public HttpRequestBuilder<Present, M> url(String url) {
            return new HttpRequestBuilder<>(url, this.method, this.headers, this.body);
        }

        // Setting method flips M: Missing -> Present
        public HttpRequestBuilder<U, Present> method(String method) {
            return new HttpRequestBuilder<>(this.url, method, this.headers, this.body);
        }

        // Optional fields preserve the existing state.
        public HttpRequestBuilder<U, M> header(String name, String value) {
            var copy = new LinkedHashMap<>(this.headers); copy.put(name, value);
            return new HttpRequestBuilder<>(this.url, this.method, copy, this.body);
        }
        public HttpRequestBuilder<U, M> body(String body) {
            return new HttpRequestBuilder<>(this.url, this.method, this.headers, body);
        }

        // Convenience aliases for common methods, also typed.
        public HttpRequestBuilder<U, Present> get()  { return method("GET");  }
        public HttpRequestBuilder<U, Present> post() { return method("POST"); }
        public HttpRequestBuilder<U, Present> put()  { return method("PUT");  }

        public static HttpRequestBuilder<Missing, Missing> empty() {
            return new HttpRequestBuilder<>(null, null, new LinkedHashMap<>(), null);
        }
    }

    // The `send()` declared as a free static method that takes the *fully built* type.
    // Because Java does not allow conditional method visibility on instance methods
    // (we cannot say "this method only exists on this exact parameterisation"), the
    // idiomatic encoding is a static method that demands the right type signature.
    public static HttpRequest send(HttpRequestBuilder<Present, Present> b) {
        // Use a defensive LinkedHashMap copy to preserve insertion order.
        return new HttpRequest(b.url, b.method, new LinkedHashMap<>(b.headers), b.body);
    }

    // Public entry point of the DSL.
    public static HttpRequestBuilder<Missing, Missing> request() {
        return HttpRequestBuilder.empty();
    }

    // =================================================================================================
    // Sections
    // =================================================================================================

    static void typeStatePattern() {
        System.out.println("[Section 2] type-state advancing call by call");

        // Each step's type is shown in the comment.
        var b0 = request();                           // HttpRequestBuilder<Missing, Missing>
        var b1 = b0.url("https://x");                 // HttpRequestBuilder<Present, Missing>
        var b2 = b1.method("GET");                    // HttpRequestBuilder<Present, Present>

        System.out.println("  b0: " + b0.getClass().getSimpleName() + " (raw runtime erases parameters)");
        System.out.println("  b1: only the URL has been set");
        System.out.println("  b2: both URL and method set; send() is now callable");

        // send(b2) compiles. send(b0) and send(b1) would not.
        System.out.println("  send(b2) -> " + send(b2).render().lines().findFirst().orElseThrow());
    }

    static void httpBuilderDemo() {
        System.out.println("[Section 4] HTTP builder happy path");
        var req = send(request()
                .url("https://api.example.com/users/42")
                .get()
                .header("Accept", "application/json"));
        System.out.println("  rendered:");
        req.render().lines().forEach(l -> System.out.println("    " + l));
    }

    static void whatYouCannotDo() {
        System.out.println("[Section 5] what would NOT compile");
        System.out.println("  send(request())                  // ✗ method send is not declared on <Missing, Missing>");
        System.out.println("  send(request().url(\"...\"))       // ✗ method send is not declared on <Present, Missing>");
        System.out.println("  send(request().method(\"GET\"))    // ✗ method send is not declared on <Missing, Present>");
        System.out.println("  Each error names the exact type-state — IDE quick-fix can suggest the missing call.");
    }

    static void endToEnd() {
        System.out.println("[Section 6] end-to-end with reference assertions");

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
                Accept: application/json
                """.stripTrailing();
        String expectedPost = """
                POST https://api.example.com/users
                Content-Type: application/json
                Idempotency-Key: abc123

                {"name":"alice"}""";

        boolean getOk  = get.render().stripTrailing().equals(expectedGet);
        boolean postOk = post.render().stripTrailing().equals(expectedPost);

        System.out.println("  GET  rendered matches expected? " + getOk);
        System.out.println("  POST rendered matches expected? " + postOk);
        System.out.println("  GET request:");
        get.render().lines().forEach(l -> System.out.println("    " + l));
        System.out.println("  POST request:");
        post.render().lines().forEach(l -> System.out.println("    " + l));
        System.out.println("  both probes match? " + (getOk && postOk));
    }

    public static void main(String[] args) {
        typeStatePattern();
        httpBuilderDemo();
        whatYouCannotDo();
        endToEnd();
        System.out.println("Mod008PhantomTypeBuilderDsl finished");
    }
}
