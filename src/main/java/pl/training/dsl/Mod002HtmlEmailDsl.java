package pl.training.dsl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/*
Why a typed HTML DSL

- Composing HTML by string concatenation is the canonical XSS factory. Every + between user input and a string is a
  potential injection point; every "I'll just escape this one" comment is a future bug.
- A typed DSL inverts the default: the only way to insert text is through an escaping API. Raw HTML must be opted
  into via an explicit, ugly-named method (unsafeRaw), so reviewers can grep for it.
- Bonus pay-off: nesting structure mirrors the DOM. Forgetting to close a tag is impossible because the closing tag
  is emitted by the builder, not the user.
- Real-world use: rendering transactional emails (welcome, password reset, invoice). The DSL produces a String ready
  to feed into an SMTP client.
*/

public final class Mod002HtmlEmailDsl {

    private Mod002HtmlEmailDsl() {}

    // ---- Attribute value record ----
    record Attr(String name, String value) {}

    static Attr href(String url)         { return new Attr("href",  url); }
    static Attr cls (String classes)     { return new Attr("class", classes); }
    static Attr id  (String id)          { return new Attr("id",    id); }
    static Attr style(String css)        { return new Attr("style", css); }
    static Attr attr(String name, String value) { return new Attr(name, value); }

    // ---- The builder ----
    static final class HtmlBuilder {
        private final StringBuilder buf;
        private int depth;
        HtmlBuilder()                        { this(new StringBuilder(), 0); }
        HtmlBuilder(StringBuilder buf, int depth) { this.buf = buf; this.depth = depth; }

        // generic block tag: open, body lambda, close
        public HtmlBuilder tag(String name, Consumer<HtmlBuilder> body, Attr... attrs) {
            indent().append('<').append(name);
            renderAttrs(attrs);
            buf.append(">\n");
            depth++;
            body.accept(this);
            depth--;
            indent().append("</").append(name).append(">\n");
            return this;
        }

        public HtmlBuilder voidTag(String name, Attr... attrs) {
            indent().append('<').append(name);
            renderAttrs(attrs);
            buf.append(">\n");
            return this;
        }

        // text node — always escaped
        public HtmlBuilder text(String s) {
            indent().append(escape(s)).append('\n');
            return this;
        }

        // explicit raw HTML — for templating composition; auditable by name
        public HtmlBuilder unsafeRaw(String html) {
            indent().append(html).append('\n');
            return this;
        }

        // common shortcuts (read like the DOM)
        public HtmlBuilder html(Consumer<HtmlBuilder> body)    { return tag("html",  body); }
        public HtmlBuilder head(Consumer<HtmlBuilder> body)    { return tag("head",  body); }
        public HtmlBuilder body(Consumer<HtmlBuilder> body, Attr... a) { return tag("body", body, a); }
        public HtmlBuilder title(String s)                     { return tag("title", b -> b.text(s)); }
        public HtmlBuilder h1(String s, Attr... a)             { return tag("h1", b -> b.text(s), a); }
        public HtmlBuilder p (String s, Attr... a)             { return tag("p",  b -> b.text(s), a); }
        public HtmlBuilder ul(Consumer<HtmlBuilder> body, Attr... a) { return tag("ul", body, a); }
        public HtmlBuilder li(String s, Attr... a)             { return tag("li", b -> b.text(s), a); }
        public HtmlBuilder a (String text, Attr... a)          { return tag("a",  b -> b.text(text), a); }
        public HtmlBuilder br()                                { return voidTag("br"); }

        // include a reusable fragment
        public HtmlBuilder include(Consumer<HtmlBuilder> fragment) {
            fragment.accept(this); return this;
        }

        public String render() { return buf.toString(); }

        // --- internals
        private StringBuilder indent() { for (int i = 0; i < depth; i++) buf.append("  "); return buf; }
        private void renderAttrs(Attr[] attrs) {
            for (var a : attrs) buf.append(' ').append(a.name()).append("=\"").append(escape(a.value())).append('"');
        }
    }

    static String escape(String s) {
        var out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&'  -> out.append("&amp;");
                case '<'  -> out.append("&lt;");
                case '>'  -> out.append("&gt;");
                case '"'  -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default   -> out.append(c);
            }
        }
        return out.toString();
    }

    static String email(Consumer<HtmlBuilder> body) {
        var b = new HtmlBuilder();
        b.unsafeRaw("<!DOCTYPE html>");
        b.html(body);
        return b.render();
    }

    /*
    Tag composition with lambda receivers

    - Each builder method takes a Consumer<HtmlBuilder> for its body. The caller writes
      b -> { b.h1("Title"); b.p("Body"); } and never sees the opening or closing tag.
    - Indentation is controlled by the renderer, not by the caller.
    - Self-closing tags (<br>, <img>) have their own helpers that take no body lambda — eliminating the "did I forget
      to close it?" question.
    */
    static void tagComposition() {
        System.out.println("[Section 2] tag composition");

        var html = email(root -> root
                .head(h -> h.title("Hello").voidTag("meta", attr("charset", "utf-8")))
                .body(b -> b.h1("Welcome!").p("This is a tiny email.")));
        System.out.println(html.lines().limit(8).reduce("", (a, l) -> a + "  " + l + "\n"));
    }

    /*
    Escape-by-construction

    - text(...) always escapes <, >, &, ", '. Plain string parameters of attribute helpers (href, cls, id, ...) are
      also escaped.
    - The only way to inject a literal <script> is to call unsafeRaw(...). That method exists for templating
      concatenation but has a name that makes audits trivial: git grep unsafeRaw shows every place trust is granted.
    - This is the same shape as Spring's HtmlUtils.htmlEscape or Apache's StringEscapeUtils.escapeHtml4, but baked
      into the construction API instead of being an optional helper.
    */
    static void escapeByConstruction() {
        System.out.println("[Section 3] escape by construction");

        var malicious = "<script>alert('xss')</script>";
        var html = email(root -> root.body(b -> b.p(malicious)));
        // The dangerous markup is rendered as visible text, not as a tag.
        if (html.contains("<script>")) {
            System.out.println("  ✗ DSL leaked raw script — bug");
        } else {
            System.out.println("  ✓ <script> tag was escaped to &lt;script&gt;");
        }

        // The unsafeRaw door is taken only deliberately.
        var trusted = email(root -> root.body(b -> b.unsafeRaw("<custom-tag>ok</custom-tag>")));
        System.out.println("  unsafeRaw passes through: contains '<custom-tag>'? "
                + trusted.contains("<custom-tag>"));
    }

    /*
    Attributes via varargs

    - Tags accept attributes through varargs of typed Attr values: a(href("/login"), cls("btn primary")).
    - Helper functions (href, cls, id, style, attr("data-foo", v)) return Attr instances, so an integer or a Date
      cannot accidentally be passed where an attribute name was expected.
    - cls accepts a space-separated list internally; a cls("a b") produces class="a b" — the helper does the right
      thing without forcing the user to remember the HTML attribute name.
    */
    static void attributesDemo() {
        System.out.println("[Section 4] attributes via varargs");

        var html = email(root -> root.body(b -> b
                .h1("Hello", id("greeting"), cls("primary large"))
                .a("Open dashboard",
                        href("https://app.example.com/dashboard?u=42"),
                        cls("btn btn-primary"),
                        attr("target", "_blank"))));
        // Print the lines that contain attributes.
        html.lines().filter(l -> l.contains("class=") || l.contains("href="))
                .forEach(l -> System.out.println("  " + l.trim()));
    }

    /*
    Reusable fragments

    - A "fragment" is just a Consumer<HtmlBuilder> — the same shape as the body lambdas. Define
      welcomeBanner(String name) returning such a consumer; reuse it inside larger emails by calling
      b -> welcomeBanner("Alice").accept(b).
    - This is the same composition rule as the lambda receivers in §2 — the DSL is uniform in how nesting is
      expressed, so reusable pieces compose naturally.
    */
    static Consumer<HtmlBuilder> welcomeBanner(String name) {
        return b -> b.tag("section",
                inner -> inner.h1("Welcome, " + name + "!").p("Thanks for joining."),
                cls("banner"));
    }

    static Consumer<HtmlBuilder> footer(String company, int year) {
        return b -> b.tag("footer",
                inner -> inner.p("© " + year + " " + company + ". All rights reserved."),
                cls("footer"));
    }

    static void reusableFragments() {
        System.out.println("[Section 5] reusable fragments");

        var html = email(root -> root.body(body -> body
                .include(welcomeBanner("Alice <admin>"))      // includes escaping inside the banner
                .include(footer("Acme", 2026))));
        System.out.println(html.lines().limit(10).reduce("", (a, l) -> a + "  " + l + "\n"));
    }

    /*
    End-to-end welcome email

    - Assemble a complete HTML email: heading, salutation, paragraph, unordered list of next steps, call-to-action
      button, footer.
    - Render to a single string, perform sanity checks (balanced tags, no unescaped < outside tag delimiters), and
      print the first lines.
    */
    static void endToEnd() {
        System.out.println("[Section 6] end-to-end welcome email");

        Map<String, String> ctaParams = new LinkedHashMap<>();
        ctaParams.put("u", "42");
        ctaParams.put("ref", "welcome-email");

        var html = email(root -> root
                .head(h -> h.title("Welcome to Acme").voidTag("meta", attr("charset", "utf-8")))
                .body(b -> b
                        .include(welcomeBanner("Alice"))
                        .p("Here's what you can do next:")
                        .ul(ul -> ul
                                .li("Set up your profile")
                                .li("Invite a teammate")
                                .li("Read the quick-start guide"))
                        .a("Open dashboard",
                                href("https://app.example.com/?u=42&ref=welcome-email"),
                                cls("btn btn-primary"))
                        .include(footer("Acme", 2026))));

        // Sanity checks: balanced tags, no unescaped angle brackets in text payload.
        long opens  = html.lines().filter(l -> l.trim().startsWith("<") && !l.trim().startsWith("</") && !l.trim().startsWith("<!")).count();
        long closes = html.lines().filter(l -> l.trim().startsWith("</")).count();
        System.out.println("  open/close-tag line balance: opens=" + opens + ", closes=" + closes);
        System.out.println("  contains escaped ampersand? " + html.contains("&amp;ref="));
        System.out.println("  first 6 lines:");
        html.lines().limit(6).forEach(l -> System.out.println("    " + l));
    }

    public static void main(String[] args) {
        tagComposition();
        escapeByConstruction();
        attributesDemo();
        reusableFragments();
        endToEnd();
        System.out.println("Mod002HtmlEmailDsl finished");
    }
}
