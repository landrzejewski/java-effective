package pl.training.concurrency.extras.chat_v3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The registry of live connections.
 *
 * <p>Two corrections over the naive version:
 * <ul>
 *   <li>Every lock is released in a {@code finally}. Broadcasting used to hold the read lock across the send loop
 *       with no finally, so a single failing write left the lock held forever.</li>
 *   <li>Broadcasting no longer performs socket I/O while holding the lock. Under the lock we only take a snapshot
 *       of the recipients; the writes happen outside it. Otherwise one stalled client blocks every other client
 *       and every new connection — the classic "blocking call inside a critical section" mistake (Mod003 §1).</li>
 * </ul>
 */
class Connections {

    private final List<Connection> connections = new ArrayList<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    void add(Connection connection) {
        lock.writeLock().lock();
        try {
            connections.add(connection);
        } finally {
            lock.writeLock().unlock();
        }
    }

    void remove(Connection connection) {
        lock.writeLock().lock();
        try {
            connections.remove(connection);
        } finally {
            lock.writeLock().unlock();
        }
    }

    void broadcast(String message) {
        List<Connection> recipients;
        lock.readLock().lock();
        try {
            recipients = List.copyOf(connections);      // cheap snapshot, lock held briefly
        } finally {
            lock.readLock().unlock();
        }
        // Slow or dead sockets can no longer block the registry.
        for (var connection : recipients) {
            connection.send(message);
            if (connection.isBroken()) {
                remove(connection);
            }
        }
    }

    int size() {
        lock.readLock().lock();
        try {
            return connections.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
