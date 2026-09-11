package pl.training.concurrency.extras.solution5;

import java.util.Locale;
import pl.training.concurrency.extras.common.ThreadUtils;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs the three hand-written bounded queues through the same producer/consumer workload and verifies that every
 * item crosses exactly once. Without a check like this a broken queue looks fine right up to the moment it is used
 * in anger — the previous version of {@link LockBlockingQueue} signalled a Condition after releasing its lock and
 * threw {@link IllegalMonitorStateException} on the very first call.
 */
public class Application {

    private static final int PRODUCERS = 4;
    private static final int CONSUMERS = 4;
    private static final int ITEMS_PER_PRODUCER = 2_500;
    private static final int CAPACITY = 8;

    /** Minimal shape shared by the three queues (none of them implements a common interface). */
    private interface Channel {
        void put(Long item) throws InterruptedException;
        Long take() throws InterruptedException;
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("[solution5] hand-written bounded queues");

        var monitorQueue = new MonitorBlockingQueue<Long>(CAPACITY);
        run("MonitorBlockingQueue", channel(monitorQueue::enqueue, monitorQueue::dequeue));

        var lockQueue = new LockBlockingQueue<Long>(CAPACITY);
        run("LockBlockingQueue", channel(lockQueue::enqueue, lockQueue::dequeue));

        var semaphoreQueue = new SemaphoreQueue<Long>(CAPACITY);
        run("SemaphoreQueue", channel(semaphoreQueue::enqueue, semaphoreQueue::dequeue));

        System.out.println("  all three queues transferred every item exactly once");
    }

    private interface Sink { void put(Long item) throws InterruptedException; }
    private interface Source { Long take() throws InterruptedException; }

    private static Channel channel(Sink sink, Source source) {
        return new Channel() {
            @Override public void put(Long item) throws InterruptedException { sink.put(item); }
            @Override public Long take() throws InterruptedException { return source.take(); }
        };
    }

    private static void run(String name, Channel channel) throws InterruptedException {
        long expectedCount = (long) PRODUCERS * ITEMS_PER_PRODUCER;
        long expectedChecksum = expectedCount * (expectedCount - 1) / 2;
        var consumed = new AtomicLong();
        var checksum = new AtomicLong();

        var producers = new ArrayList<Thread>();
        for (int p = 0; p < PRODUCERS; p++) {
            final long base = (long) p * ITEMS_PER_PRODUCER;
            producers.add(ThreadUtils.asyncRun(name + "-producer-" + p, () -> {
                for (int i = 0; i < ITEMS_PER_PRODUCER; i++) {
                    channel.put(base + i);
                }
            }));
        }
        var consumers = new ArrayList<Thread>();
        for (int c = 0; c < CONSUMERS; c++) {
            consumers.add(ThreadUtils.asyncRun(name + "-consumer-" + c, () -> {
                Long item;
                while ((item = channel.take()) != null) {   // null is the poison pill
                    consumed.incrementAndGet();
                    checksum.addAndGet(item);
                }
            }));
        }

        long start = System.nanoTime();
        producers.forEach(Thread::start);
        consumers.forEach(Thread::start);
        for (var producer : producers) {
            producer.join();
        }
        for (int c = 0; c < CONSUMERS; c++) {
            channel.put(null);                              // one pill per consumer
        }
        for (var consumer : consumers) {
            consumer.join();
        }
        long millis = (System.nanoTime() - start) / 1_000_000;

        boolean ok = consumed.get() == expectedCount && checksum.get() == expectedChecksum;
        System.out.printf(Locale.ROOT, "  %-22s consumed %,d/%,d, checksum %s (%d ms)%n",
                name, consumed.get(), expectedCount, ok ? "OK" : "MISMATCH", millis);
        if (!ok) {
            throw new AssertionError(name + " lost or duplicated items");
        }
    }
}
