/**
 * Chat server, variant 1 of 3: <b>a thread per connection</b>.
 *
 * <p>The most direct translation of the problem into code — {@code accept()} in a loop, one thread per client,
 * blocking reads and writes. Historically this design hit a wall at a few thousand clients because every
 * connection cost a platform thread; with virtual threads (Mod011) that ceiling is gone and the simple shape is
 * once again the right one.
 *
 * <p>Compare {@link pl.training.concurrency.extras.chat_v2} (a bounded executor plus an event bus) and
 * {@link pl.training.concurrency.extras.chat_v3} (RxJava), which are the two things people reached for while the
 * thread-per-connection model was too expensive.
 */
package pl.training.concurrency.extras.chat_v1;
