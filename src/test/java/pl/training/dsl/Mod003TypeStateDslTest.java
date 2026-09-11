package pl.training.dsl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static pl.training.dsl.Mod003TypeStateDsl.http;
import static pl.training.dsl.Mod003TypeStateDsl.request;
import static pl.training.dsl.Mod003TypeStateDsl.send;

/*
The rejections this DSL exists for are compile errors, so they cannot be asserted from a test — a test that
does not compile is not a failing test. They are documented in Mod003TypeStateDsl §3 instead. What is testable
is that the accepted chains produce exactly the request they describe.
*/
class Mod003TypeStateDslTest {

    @Test
    @DisplayName("a fully configured builder renders the request it describes")
    void rendersConfiguredRequest() {
        var request = send(request()
                .url("https://api.example.com/users")
                .post()
                .header("Content-Type", "application/json")
                .body("{\"name\":\"alice\"}"));

        assertEquals("""
                POST https://api.example.com/users
                Content-Type: application/json

                {"name":"alice"}""", request.render());
    }

    @Test
    @DisplayName("headers keep insertion order — Map.copyOf would not")
    void preservesHeaderOrder() {
        var request = send(request().url("https://x").get()
                .header("zeta", "1")
                .header("alpha", "2")
                .header("mid", "3"));

        assertEquals(List.of("zeta", "alpha", "mid"), List.copyOf(request.headers().keySet()));
    }

    @Test
    @DisplayName("the built request is immutable")
    void headersAreUnmodifiable() {
        var request = send(request().url("https://x").get().header("a", "1"));

        assertThrows(UnsupportedOperationException.class, () -> request.headers().put("b", "2"));
    }

    @Test
    @DisplayName("each step returns a new builder, so a partial one can be reused")
    void buildersAreImmutableValues() {
        var base = request().url("https://api.example.com").header("Accept", "application/json");

        var get = send(base.get());
        var post = send(base.post().body("{}"));

        assertAll(
                () -> assertEquals("GET", get.method()),
                () -> assertEquals("POST", post.method()),
                () -> assertEquals("application/json", get.headers().get("Accept")),
                () -> assertEquals(null, get.body(), "the body set on `post` must not leak into `get`"));
    }

    @Test
    @DisplayName("the staged-interface encoding builds the same request")
    void stagedEncodingAgrees() {
        var staged = http().url("https://api.example.com/users/42").get()
                .header("Accept", "application/json")
                .send();
        var phantom = send(request().url("https://api.example.com/users/42").get()
                .header("Accept", "application/json"));

        assertEquals(phantom.render(), staged.render());
    }
}
