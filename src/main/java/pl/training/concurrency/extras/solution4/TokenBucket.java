package pl.training.concurrency.extras.solution4;

/**
 * A token-bucket rate limiter: at most {@code capacity} tokens are held, and one new token is generated every
 * {@code refillIntervalMillis}.
 *
 * <p>The previous version of this class had three bugs worth naming, because each is a common one:
 * <ol>
 *   <li>It called {@link Thread#sleep} from inside a {@code synchronized} method. Sleeping does NOT release a
 *       monitor (Mod001 §6), so one waiting caller blocked every other caller — including ones that had tokens
 *       waiting for them. The fix is {@code wait(timeout)}, which releases the monitor while it waits.</li>
 *   <li>When no token was available it slept and then returned anyway, handing out a token that never existed.
 *       A blocking limiter must loop until it actually consumes one.</li>
 *   <li>It advanced {@code lastRefill} to "now" on every call, throwing away the remainder of the integer
 *       division. Under frequent calls the bucket then refilled strictly slower than configured. The fix is to
 *       advance it by exactly the amount of time consumed by the tokens that were generated.</li>
 * </ol>
 */
public class TokenBucket {

    private final Object monitor = new Object();
    private final int capacity;
    private final long refillIntervalMillis;

    private long tokens;
    private long lastRefill = System.currentTimeMillis();

    public TokenBucket(int capacity, long refillIntervalMillis) {
        if (capacity <= 0 || refillIntervalMillis <= 0) {
            throw new IllegalArgumentException("capacity and refillIntervalMillis must be positive");
        }
        this.capacity = capacity;
        this.refillIntervalMillis = refillIntervalMillis;
        this.tokens = capacity;     // a bucket starts full: the first burst is allowed
    }

    /** Blocks until a token is available, then consumes it. */
    public void getToken() throws InterruptedException {
        synchronized (monitor) {
            while (true) {
                refill();
                if (tokens > 0) {
                    tokens--;
                    return;
                }
                // wait() releases the monitor; sleep() would not.
                monitor.wait(millisUntilNextToken());
            }
        }
    }

    /** Non-blocking variant: consumes a token if one is available, otherwise gives up immediately. */
    public boolean tryGetToken() {
        synchronized (monitor) {
            refill();
            if (tokens > 0) {
                tokens--;
                return true;
            }
            return false;
        }
    }

    public long availableTokens() {
        synchronized (monitor) {
            refill();
            return tokens;
        }
    }

    private void refill() {
        long elapsed = System.currentTimeMillis() - lastRefill;
        long generated = elapsed / refillIntervalMillis;
        if (generated > 0) {
            tokens = Math.min(capacity, tokens + generated);
            lastRefill += generated * refillIntervalMillis;   // keep the remainder
        }
    }

    private long millisUntilNextToken() {
        long consumedOfCurrentInterval = System.currentTimeMillis() - lastRefill;
        return Math.max(1, refillIntervalMillis - consumedOfCurrentInterval);
    }
}
