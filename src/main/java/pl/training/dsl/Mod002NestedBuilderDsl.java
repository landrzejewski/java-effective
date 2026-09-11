package pl.training.dsl;

import java.util.Locale;
import java.util.function.Consumer;

/*
Nested builders, and safety by construction

- Composing HTML by string concatenation is the classic XSS factory: every `+` between user data and markup is
  an injection point, and every "I escaped this one" comment is a future bug.
- A builder DSL inverts the default. The only way to put text into the document is through an API that escapes,
  and raw markup needs an explicitly ugly method (`unsafeRaw`) that reviewers can grep for. Safety stops being a
  thing you remember and becomes a thing you would have to work around.
- The nesting mechanism is the one from Mod001 §3: a body is a Consumer<HtmlBuilder>. The builder writes the
  opening tag, runs the lambda, then writes the closing tag — so tags can never be unbalanced, and indentation is
  the renderer's job rather than the caller's.
- Because a body is an ordinary value, a reusable fragment is just a Consumer you can name and pass around.
- Anchor for this module: a transactional e-mail (welcome, password reset, invoice) rendered to a String.
*/

public final class Mod002NestedBuilderDsl {

    private Mod002NestedBuilderDsl() {}

    // =================================================================================================
    // Attributes — a typed value per attribute, produced by named factories (Mod001 §4)
    // =================================================================================================

    record Attr(String name, String value) {}

    static Attr id(String id)                   { return new Attr("id", id); }
    static Attr attr(String name, String value) { return new Attr(name, value); }

    /** Class names are passed through as-is; the helper only supplies the HTML attribute name. */
    static Attr cls(String classNames)          { return new Attr("class", classNames); }

    /*
    Escaping a URL is not the same as validating it. `escape` turns `<` into `&lt;`, but
    `javascript:alert(1)` contains no character that escaping touches — it survives intact and still
    executes on click. A scheme whitelist is the only thing that stops it, so `href` is the one attribute
    helper here that rejects its input rather than merely encoding it.
    */
    static Attr href(String url) {
        // Validate and store the SAME string: checking the stripped form and emitting the raw one would
        // let href("  https://x") render with its leading blanks intact.
        var normalised = url.strip();
        var lower = normalised.toLowerCase(Locale.ROOT);
        boolean relative = lower.startsWith("/") && !lower.startsWith("//");   // "//host/x" is absolute!
        boolean allowed = lower.startsWith("http://") || lower.startsWith("https://")
                || lower.startsWith("mailto:") || relative || lower.startsWith("#");
        if (!allowed) throw new IllegalArgumentException("unsupported URL scheme: " + url);
        return new Attr("href", normalised);
    }

    // =================================================================================================
    // The builder
    // =================================================================================================

    static final class HtmlBuilder {
        private final StringBuilder out = new StringBuilder();
        private int depth;

        /** Block tag: open, run the body against this same builder, close. */
        HtmlBuilder tag(String name, Consumer<HtmlBuilder> body, Attr... attrs) {
            open(name, attrs);
            depth++;
            body.accept(this);
            depth--;
            indent().append("</").append(name).append(">\n");
            return this;
        }

        /** Void tag (<meta>, <br>): no body, therefore no closing tag. */
        HtmlBuilder voidTag(String name, Attr... attrs) {
            open(name, attrs);
            return this;
        }

        /** The only way to add text — always escaped. */
        HtmlBuilder text(String s) {
            indent().append(escape(s)).append('\n');
            return this;
        }

        /** The deliberate way out, named so `git grep unsafeRaw` finds every place trust was granted. */
        HtmlBuilder unsafeRaw(String html) {
            indent().append(html).append('\n');
            return this;
        }

        /** Splice in a reusable fragment (Mod001 §3: a body is a value). */
        HtmlBuilder include(Consumer<HtmlBuilder> fragment) {
            fragment.accept(this);
            return this;
        }

        // Shortcuts that read like the DOM. Each is one line because `tag` already does the work.
        HtmlBuilder head(Consumer<HtmlBuilder> body)          { return tag("head", body); }
        HtmlBuilder body(Consumer<HtmlBuilder> body)          { return tag("body", body); }
        HtmlBuilder title(String s)                           { return tag("title", b -> b.text(s)); }
        HtmlBuilder h1(String s, Attr... a)                   { return tag("h1", b -> b.text(s), a); }
        HtmlBuilder p(String s, Attr... a)                    { return tag("p", b -> b.text(s), a); }
        HtmlBuilder ul(Consumer<HtmlBuilder> body, Attr... a) { return tag("ul", body, a); }
        HtmlBuilder li(String s, Attr... a)                   { return tag("li", b -> b.text(s), a); }
        HtmlBuilder a(String text, Attr... a)                 { return tag("a", b -> b.text(text), a); }

        String render() { return out.toString(); }

        private void open(String name, Attr[] attrs) {
            indent().append('<').append(name);
            for (var a : attrs) {
                out.append(' ').append(a.name()).append("=\"").append(escape(a.value())).append('"');
            }
            out.append(">\n");
        }

        private StringBuilder indent() {
            out.append("  ".repeat(depth));
            return out;
        }
    }

    static String escape(String s) {
        var escaped = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&'  -> escaped.append("&amp;");
                case '<'  -> escaped.append("&lt;");
                case '>'  -> escaped.append("&gt;");
                case '"'  -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default   -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    /** Entry point of the DSL (Mod001 §2): the builder is never constructed by the caller. */
    static String email(Consumer<HtmlBuilder> body) {
        var builder = new HtmlBuilder();
        builder.unsafeRaw("<!DOCTYPE html>");
        builder.tag("html", body);
        return builder.render();
    }

    // =================================================================================================
    // Sections
    // =================================================================================================

    /*
    Nesting via lambda bodies

    - Every structural method takes a Consumer<HtmlBuilder>. The caller writes b -> b.h1(...).p(...) and never
      types an opening or a closing tag, so the two cannot drift apart.
    - Depth is tracked by the builder, so the output is indented without the caller counting spaces.
    - Void tags have their own method with no body parameter, which removes the "was I supposed to close this?"
      question entirely.
    */
    static void nesting() {
        System.out.println("[Section 1] nesting via lambda bodies");

        var html = email(root -> root
                .head(h -> h.title("Hello").voidTag("meta", attr("charset", "utf-8")))
                .body(b -> b.h1("Welcome!").p("A tiny e-mail.")));

        html.lines().forEach(line -> System.out.println("  " + line));
    }

    /*
    Escaping by construction

    - text(...) and every attribute value are escaped on the way out; there is no unescaped path except
      unsafeRaw, whose name is the audit trail.
    - This is the same escaping that Spring's HtmlUtils.htmlEscape or Apache's StringEscapeUtils.escapeHtml4
      perform, with one difference that matters: here it is not an optional helper you must remember to call,
      it is the only door.
    - Escaping is necessary but not sufficient — see the comment on href above.
    */
    static void escapingByConstruction() {
        System.out.println("[Section 2] escaping by construction");

        var malicious = "<script>alert('xss')</script>";
        var html = email(root -> root.body(b -> b.p(malicious)));
        System.out.println("  raw <script> present in output? " + html.contains("<script>") + "  (want: false)");
        System.out.println("  escaped as &lt;script&gt;?        " + html.contains("&lt;script&gt;"));

        var trusted = email(root -> root.body(b -> b.unsafeRaw("<custom-tag>ok</custom-tag>")));
        System.out.println("  unsafeRaw passes markup through? " + trusted.contains("<custom-tag>"));

        try {
            href("javascript:alert(1)");
            System.out.println("  javascript: URL accepted — BUG");
        } catch (IllegalArgumentException e) {
            System.out.println("  javascript: URL rejected by href(): " + e.getMessage());
        }
    }

    /*
    Reusable fragments

    - A fragment is a Consumer<HtmlBuilder> — exactly the type the body parameters already take. So a named
      fragment needs no new concept and no new interface: it is a value of a type the DSL already speaks.
    - Fragments take ordinary Java parameters, compose by calling one another, and inherit the escaping rules
      of whatever they call.
    */
    static Consumer<HtmlBuilder> banner(String name) {
        return b -> b.tag("section", inner -> inner.h1("Welcome, " + name + "!").p("Thanks for joining."),
                cls("banner"));
    }

    static Consumer<HtmlBuilder> footer(String company, int year) {
        return b -> b.tag("footer", inner -> inner.p("(c) " + year + " " + company + "."), cls("footer"));
    }

    static void reusableFragments() {
        System.out.println("[Section 3] reusable fragments are just values");

        // The name below contains markup; the fragment inherits text()'s escaping without doing anything.
        var html = email(root -> root.body(b -> b
                .include(banner("Alice <admin>"))
                .include(footer("Acme", 2026))));

        html.lines().filter(l -> l.contains("Welcome") || l.contains("(c)"))
                .forEach(line -> System.out.println("  " + line.strip()));
    }

    /*
    End-to-end welcome e-mail, with checks

    - Assemble heading, intro, a list of next steps, a call-to-action link and a footer.
    - Then verify the properties this DSL is supposed to guarantee: the ampersand in the query string is
      encoded, no unescaped <script> survived, and every opening tag has a matching close.
    */
    static void endToEnd() {
        System.out.println("[Section 4] end-to-end welcome e-mail");

        var html = email(root -> root
                .head(h -> h.title("Welcome to Acme").voidTag("meta", attr("charset", "utf-8")))
                .body(b -> b
                        .include(banner("Alice"))
                        .p("Here is what you can do next:", id("intro"))
                        .ul(ul -> ul
                                .li("Set up your profile")
                                .li("Invite a teammate")
                                .li("Read the quick-start guide"))
                        .a("Open dashboard",
                                href("https://app.example.com/?u=42&ref=welcome-email"),
                                cls("btn btn-primary"))
                        .include(footer("Acme", 2026))));

        boolean ampersandEncoded = html.contains("&amp;ref=welcome-email");
        boolean noRawScript      = !html.contains("<script>");
        boolean balanced         = tagsBalanced(html);

        System.out.println("  '&' inside the href encoded?  " + ampersandEncoded);
        System.out.println("  no raw <script> in output?    " + noRawScript);
        System.out.println("  every opened tag closed?      " + balanced);
        System.out.println("  all checks pass?              "
                + (ampersandEncoded && noRawScript && balanced));
        System.out.println("  first 8 lines:");
        html.lines().limit(8).forEach(line -> System.out.println("    " + line));
    }

    /** The HTML void elements - they carry no closing tag, so they are never pushed onto the stack. */
    private static final java.util.Set<String> VOID_TAGS = java.util.Set.of(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr");

    /**
     * True when every opening tag has a matching closing tag in the right order.
     * Void tags carry no closing tag, so they are skipped rather than counted as unbalanced.
     */
    static boolean tagsBalanced(String html) {
        var stack = new java.util.ArrayDeque<String>();
        var matcher = java.util.regex.Pattern.compile("<(/?)([a-zA-Z][-a-zA-Z0-9]*)").matcher(html);
        while (matcher.find()) {
            String name = matcher.group(2);
            if (matcher.group(1).isEmpty()) {
                if (!VOID_TAGS.contains(name.toLowerCase(Locale.ROOT))) stack.push(name);
            } else if (stack.isEmpty() || !stack.pop().equals(name)) {
                return false;
            }
        }
        return stack.isEmpty();
    }

    public static void main(String[] args) {
        nesting();
        escapingByConstruction();
        reusableFragments();
        endToEnd();
        System.out.println("Mod002NestedBuilderDsl finished");
    }
}
