package pl.training.dsl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
What a routing DSL gives you

- A routing DSL declares the mapping from (method, path-pattern) to a handler function. The whole routing table sits
  in one place; routes become greppable, code-reviewable, and reorderable.
- This module is Javalin-flavoured: app.get("/orders/{id}", ctx -> ...) with path parameters, before-filters,
  after-filters, and grouped routes. A real framework would also handle content negotiation, async, and WebSocket —
  out of scope here.
- Compare with JAX-RS annotations: the annotation form spreads the route table across many classes; the DSL keeps it
  in one method that reads top-to-bottom.
*/

public final class Mod004RestRoutingDsl {

    private Mod004RestRoutingDsl() {}

    // =================================================================================================
    // Domain
    // =================================================================================================

    record Order(String id, String customer, double amount, String status) {}

    private static final Map<String, Order> ORDERS = new LinkedHashMap<>();
    static {
        ORDERS.put("o-1", new Order("o-1", "alice", 119.50, "PAID"));
        ORDERS.put("o-2", new Order("o-2", "bob",   45.00, "PENDING"));
        ORDERS.put("o-3", new Order("o-3", "carla", 12.50, "PAID"));
    }
    private static int nextOrderId = ORDERS.size() + 1;

    // =================================================================================================
    // Request / Response
    // =================================================================================================

    record Request(String method, String path,
                   Map<String, String> pathParams,
                   Map<String, String> queryParams,
                   Map<String, String> headers,
                   String body) {
        public Optional<String> pathParam(String name)  { return Optional.ofNullable(pathParams.get(name)); }
        public Optional<String> queryParam(String name) { return Optional.ofNullable(queryParams.get(name)); }
        public Optional<String> header(String name)     { return Optional.ofNullable(headers.get(name)); }
    }

    record Response(int status, Map<String, String> headers, String body) {
        public static Response ok(String body)         { return new Response(200, Map.of(), body); }
        public static Response created(String body)    { return new Response(201, Map.of(), body); }
        public static Response notFound(String body)   { return new Response(404, Map.of(), body); }
        public static Response unauthorized(String b)  { return new Response(401, Map.of(), b); }
        public static Response badRequest(String body) { return new Response(400, Map.of(), body); }

        public Response withHeader(String n, String v) {
            var copy = new LinkedHashMap<>(this.headers); copy.put(n, v);
            return new Response(this.status, Map.copyOf(copy), this.body);
        }
    }

    @FunctionalInterface interface Handler { Response handle(Request req); }
    @FunctionalInterface interface Before  { Response apply (Request req); }      // null → continue
    @FunctionalInterface interface After   { Response apply (Request req, Response res); }

    // =================================================================================================
    // Compiled route
    // =================================================================================================

    static final class Route {
        final String method;
        final String pattern;
        final Pattern regex;
        final List<String> paramNames;
        final Handler handler;

        Route(String method, String pattern, Handler handler) {
            this.method = method; this.pattern = pattern; this.handler = handler;
            this.paramNames = new ArrayList<>();
            // Compile {name} placeholders into ([^/]+) capturing groups.
            var sb = new StringBuilder("^");
            int i = 0;
            while (i < pattern.length()) {
                char c = pattern.charAt(i);
                if (c == '{') {
                    int end = pattern.indexOf('}', i);
                    paramNames.add(pattern.substring(i + 1, end));
                    sb.append("([^/]+)");
                    i = end + 1;
                } else {
                    if ("\\.[]()*+?^$|".indexOf(c) >= 0) sb.append('\\');
                    sb.append(c); i++;
                }
            }
            sb.append('$');
            this.regex = Pattern.compile(sb.toString());
        }

        Map<String, String> matchPathParams(String path) {
            Matcher m = regex.matcher(path);
            if (!m.matches()) return null;
            var params = new LinkedHashMap<String, String>();
            for (int i = 0; i < paramNames.size(); i++) params.put(paramNames.get(i), m.group(i + 1));
            return params;
        }
    }

    // =================================================================================================
    // Router DSL — supports prefix groups, before/after middleware
    // =================================================================================================

    static final class Router {
        private final List<Route> routes = new ArrayList<>();
        private final List<Before> beforeFilters = new ArrayList<>();
        private final List<After>  afterFilters  = new ArrayList<>();
        private String prefix = "";

        public Router get   (String path, Handler h) { return register("GET",    path, h); }
        public Router post  (String path, Handler h) { return register("POST",   path, h); }
        public Router put   (String path, Handler h) { return register("PUT",    path, h); }
        public Router delete(String path, Handler h) { return register("DELETE", path, h); }

        public Router before(Before f) { beforeFilters.add(f); return this; }
        public Router after (After  f) { afterFilters .add(f); return this; }

        public Router path(String segment, Runnable inside) {
            String saved = this.prefix;
            this.prefix = saved + segment;
            try { inside.run(); } finally { this.prefix = saved; }
            return this;
        }

        private Router register(String method, String pathSuffix, Handler h) {
            routes.add(new Route(method, this.prefix + pathSuffix, h));
            return this;
        }

        public Response dispatch(Request req) {
            // Run before-filters in registration order; first non-null short-circuits.
            for (var f : beforeFilters) {
                Response early = f.apply(req);
                if (early != null) return runAfterFilters(req, early);
            }
            // First matching route wins.
            for (var r : routes) {
                if (!r.method.equals(req.method)) continue;
                Map<String, String> pp = r.matchPathParams(req.path);
                if (pp == null) continue;
                Request enriched = new Request(req.method, req.path, pp,
                        req.queryParams, req.headers, req.body);
                Response res = r.handler.handle(enriched);
                return runAfterFilters(req, res);
            }
            return runAfterFilters(req, Response.notFound("no route for " + req.method + " " + req.path));
        }

        private Response runAfterFilters(Request req, Response res) {
            for (var f : afterFilters) res = f.apply(req, res);
            return res;
        }
    }

    // =================================================================================================
    // Sections
    // =================================================================================================

    /*
    Method + path pattern matching

    - A path pattern like /orders/{id}/items/{itemId} is compiled once into a regex
      (^/orders/([^/]+)/items/([^/]+)$) plus an ordered list of parameter names.
    - At dispatch time the request path is matched against each compiled pattern in registration order; the first
      match wins.
    - The handler receives a Request whose pathParam(name) looks up by name. The user never deals with regex groups
      directly.
    */
    static void patternMatching() {
        System.out.println("[Section 2] path-pattern matching");
        Route r = new Route("GET", "/orders/{id}/items/{itemId}",
                req -> Response.ok("hit " + req.pathParams));
        var match = r.matchPathParams("/orders/o-1/items/sku-42");
        System.out.println("  /orders/o-1/items/sku-42 -> " + match);
        System.out.println("  /other                  -> " + r.matchPathParams("/other"));
    }

    /*
    Typed Request / Response

    - Request exposes method, path, pathParam(name), queryParam(name), header(name), and body().
    - Response is an immutable record (status, headers, body). Helpers Response.ok(...), Response.notFound(...),
      Response.badRequest(...) cover the common shapes without making the call site write status codes by hand.
    - Typed "handler" is just Function<Request, Response> — same shape as servlets, but composable with regular Java
      functions.
    */
    static void typedShapes() {
        System.out.println("[Section 3] Request / Response shapes");
        var req = new Request("GET", "/orders/o-1",
                Map.of("id", "o-1"), Map.of("verbose", "1"),
                Map.of("Authorization", "Bearer t-xyz"), "");
        System.out.println("  pathParam(id)        = " + req.pathParam("id").orElse("?"));
        System.out.println("  queryParam(verbose)  = " + req.queryParam("verbose").orElse("?"));
        System.out.println("  header(Auth) prefix? " + req.header("Authorization").map(s -> s.startsWith("Bearer ")).orElse(false));
        System.out.println("  Response.ok          = " + Response.ok("body"));
    }

    /*
    Middleware (before / after)

    - A before filter runs prior to the handler and can short-circuit by returning a Response (auth fails, rate
      limit exceeded). Returning null lets the chain proceed.
    - An after filter receives the produced Response and can rewrite it — adding common headers, wrapping errors,
      gzip-encoding the body.
    - The chain composes left-to-right in registration order; in real frameworks middleware is the first thing teams
      customise.
    */
    private static Before requireBearerAuth() {
        return req -> req.header("Authorization").filter(h -> h.startsWith("Bearer ")).isPresent()
                ? null
                : Response.unauthorized("missing or invalid Authorization header");
    }
    private static After forceJsonContentType() {
        return (req, res) -> res.headers.containsKey("Content-Type")
                ? res
                : res.withHeader("Content-Type", "application/json");
    }

    static void middlewareDemo() {
        System.out.println("[Section 4] middleware");
        Before b = requireBearerAuth();
        var unauthed = new Request("GET", "/x", Map.of(), Map.of(), Map.of(), "");
        var authed   = new Request("GET", "/x", Map.of(), Map.of(), Map.of("Authorization", "Bearer t-xyz"), "");
        System.out.println("  unauthed → " + b.apply(unauthed));
        System.out.println("  authed   → " + (b.apply(authed) == null ? "<continue>" : "rejected"));
    }

    /*
    Grouped routes

    - path("/orders", () -> { get("/", list); post("/", create); ... }) prepends the prefix to every route
      registered inside the lambda. Cuts the path duplication that creeps into REST APIs over time.
    - Groups can nest. The DSL maintains a stack of prefixes that is restored when the lambda returns.
    */
    static Router buildOrderService() {
        var router = new Router();

        router.before(requireBearerAuth());
        router.after(forceJsonContentType());

        router.path("/orders", () -> {
            router.get("",        Mod004RestRoutingDsl::listOrders);
            router.post("",       Mod004RestRoutingDsl::createOrder);
            router.get("/{id}",   Mod004RestRoutingDsl::getOrder);
            router.delete("/{id}", Mod004RestRoutingDsl::cancelOrder);
        });

        return router;
    }

    static void groupedRoutes() {
        System.out.println("[Section 5] grouped routes");
        var router = buildOrderService();
        for (var r : router.routes) System.out.println("  " + String.format("%-7s %s", r.method, r.pattern));
    }

    /*
    End-to-end on the order service

    - Register four endpoints: GET /orders/{id}, GET /orders, POST /orders, DELETE /orders/{id}.
    - Wire one before-filter (require Authorization: Bearer …) and one after-filter (force
      Content-Type: application/json).
    - Simulate five requests (one missing auth, one nonexistent id, one successful POST, etc.) and print the
      resulting (status, body) pair.
    - A small reference table validates that each simulated request gets the expected status code.
    */
    static void endToEnd() {
        System.out.println("[Section 6] end-to-end + assertions");
        var router = buildOrderService();
        var bearer = Map.of("Authorization", "Bearer t-xyz");
        var noAuth = Map.<String, String>of();

        record Probe(Request request, int expectedStatus, String label) {}
        var probes = List.of(
                new Probe(new Request("GET", "/orders",       Map.of(), Map.of(), bearer, ""), 200, "list"),
                new Probe(new Request("GET", "/orders/o-1",   Map.of(), Map.of(), bearer, ""), 200, "get hit"),
                new Probe(new Request("GET", "/orders/o-99",  Map.of(), Map.of(), bearer, ""), 404, "get miss"),
                new Probe(new Request("POST", "/orders",      Map.of(), Map.of(), bearer,
                                "{customer:'dave',amount:50.0}"),                              201, "create"),
                new Probe(new Request("GET", "/orders/o-1",   Map.of(), Map.of(), noAuth, ""), 401, "no auth"));

        boolean allOk = true;
        for (var p : probes) {
            Response res = router.dispatch(p.request);
            boolean ok = res.status == p.expectedStatus;
            if (!ok) allOk = false;
            System.out.printf("  [%-9s] %-6s %-15s → %d %-30s %s%n",
                    p.label, p.request.method, p.request.path,
                    res.status, res.body, ok ? "✓" : "✗");
        }
        System.out.println("  all probes match expected status? " + allOk);
    }

    // =================================================================================================
    // Handlers
    // =================================================================================================

    static Response listOrders(Request req) {
        return Response.ok(ORDERS.values().toString());
    }
    static Response getOrder(Request req) {
        return req.pathParam("id")
                .map(ORDERS::get)
                .map(o -> Response.ok(o.toString()))
                .orElse(Response.notFound("order not found"));
    }
    static Response createOrder(Request req) {
        // Body parsing is fake for the demo.
        var id = "o-" + nextOrderId++;
        var created = new Order(id, "dave", 50.00, "PENDING");
        ORDERS.put(id, created);
        return Response.created(created.toString());
    }
    static Response cancelOrder(Request req) {
        Optional<String> idOpt = req.pathParam("id");
        if (idOpt.isEmpty() || !ORDERS.containsKey(idOpt.get())) return Response.notFound("order not found");
        ORDERS.remove(idOpt.get());
        return Response.ok("cancelled " + idOpt.get());
    }

    public static void main(String[] args) {
        patternMatching();
        typedShapes();
        middlewareDemo();
        groupedRoutes();
        endToEnd();
        System.out.println("Mod004RestRoutingDsl finished");
    }

    @SuppressWarnings("unused")
    private static Map<String, String> empty() { return Collections.emptyMap(); }
}
