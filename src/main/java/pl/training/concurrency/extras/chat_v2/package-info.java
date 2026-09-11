/**
 * Chat server, variant 2 of 3: <b>an executor plus an event bus</b>.
 *
 * <p>Connections are handed to an {@link java.util.concurrent.ExecutorService} (Mod007) and the components talk to
 * each other through {@link pl.training.concurrency.extras.chat_v2.EventsBus} instead of calling one another
 * directly, so logging, history and broadcasting are independent consumers of the same event stream.
 *
 * <p>Two concurrency lessons live here: the listener registry belongs in a
 * {@link java.util.concurrent.CopyOnWriteArraySet} rather than a synchronized {@code HashSet} (Mod005), and the
 * thread-safety of the worker registry is a decorator ({@code SynchronizedServiceWorkers}) rather than a property
 * baked into the collection.
 */
package pl.training.concurrency.extras.chat_v2;
