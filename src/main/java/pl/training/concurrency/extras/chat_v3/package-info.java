/**
 * Chat server, variant 3 of 3: <b>RxJava</b>.
 *
 * <p>Connections and incoming lines become {@link io.reactivex.rxjava3.core.Observable} streams, and threading is
 * expressed declaratively with {@code subscribeOn} / {@code observeOn} instead of by creating threads.
 *
 * <p>Worth comparing with Mod009 (CompletableFuture) and Mod012 (StructuredTaskScope): all three are answers to
 * "how do I run several things at once without a thread each", and the JDK's own answers now cover most of what
 * this variant was originally needed for.
 */
package pl.training.concurrency.extras.chat_v3;
