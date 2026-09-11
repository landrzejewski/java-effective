package pl.training.dsl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.training.dsl.Mod002NestedBuilderDsl.attr;
import static pl.training.dsl.Mod002NestedBuilderDsl.banner;
import static pl.training.dsl.Mod002NestedBuilderDsl.cls;
import static pl.training.dsl.Mod002NestedBuilderDsl.email;
import static pl.training.dsl.Mod002NestedBuilderDsl.href;
import static pl.training.dsl.Mod002NestedBuilderDsl.tagsBalanced;

class Mod002NestedBuilderDslTest {

    @Test
    @DisplayName("text() escapes markup instead of emitting it")
    void escapesText() {
        var html = email(root -> root.body(b -> b.p("<script>alert('xss')</script>")));

        assertAll(
                () -> assertFalse(html.contains("<script>"), "raw script tag leaked"),
                () -> assertTrue(html.contains("&lt;script&gt;")),
                () -> assertTrue(html.contains("&#39;xss&#39;"), "single quotes must be escaped too"));
    }

    @Test
    @DisplayName("attribute values are escaped, so a quote cannot break out of the attribute")
    void escapesAttributeValues() {
        var html = email(root -> root.body(b -> b.p("x", attr("title", "a \" onload=\"evil()"))));

        assertTrue(html.contains("title=\"a &quot; onload=&quot;evil()\""));
    }

    @Test
    @DisplayName("unsafeRaw is the only path that passes markup through untouched")
    void unsafeRawPassesThrough() {
        var html = email(root -> root.body(b -> b.unsafeRaw("<custom-tag>ok</custom-tag>")));

        assertTrue(html.contains("<custom-tag>ok</custom-tag>"));
    }

    @Test
    @DisplayName("href rejects schemes that escaping alone would not neutralise")
    void hrefRejectsDangerousSchemes() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> href("javascript:alert(1)")),
                () -> assertThrows(IllegalArgumentException.class, () -> href("data:text/html;base64,AAAA")),
                () -> assertEquals("https://example.com", href("https://example.com").value()),
                () -> assertEquals("/orders", href("/orders").value()),
                () -> assertEquals("mailto:a@example.com", href("mailto:a@example.com").value()));
    }

    @Test
    @DisplayName("a fragment is a value, so including it twice renders it twice")
    void fragmentsAreReusableValues() {
        var html = email(root -> root.body(b -> b.include(banner("Alice")).include(banner("Bob"))));

        assertAll(
                () -> assertTrue(html.contains("Welcome, Alice!")),
                () -> assertTrue(html.contains("Welcome, Bob!")),
                () -> assertEquals(2, html.lines().filter(line -> line.contains("Thanks for joining")).count()));
    }

    @Test
    @DisplayName("the builder closes every tag it opens, including inside fragments")
    void tagsAreAlwaysBalanced() {
        var html = email(root -> root
                .head(h -> h.title("t").voidTag("meta", attr("charset", "utf-8")))
                .body(b -> b
                        .include(banner("Alice"))
                        .ul(ul -> ul.li("one").li("two"), cls("steps"))
                        .a("go", href("https://example.com/?a=1&b=2"))));

        assertAll(
                () -> assertTrue(tagsBalanced(html)),
                () -> assertTrue(html.contains("&amp;b=2"), "'&' in a URL must be encoded"));
    }

    @Test
    @DisplayName("tagsBalanced actually detects an unbalanced document")
    void balanceCheckCanFail() {
        assertFalse(tagsBalanced("<html><body></html>"));
    }
}
