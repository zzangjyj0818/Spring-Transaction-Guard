package io.github.zzangjyj0818.transactionguard.spring.transaction;

/** Monotonic nanosecond time source used for elapsed-time measurements. */
@FunctionalInterface
public interface MonotonicClock {

    /**
     * Returns the current monotonic time.
     *
     * @return arbitrary-origin monotonic nanosecond value
     */
    long nanoTime();

    /**
     * Returns the system monotonic clock.
     *
     * @return clock backed by {@link System#nanoTime()}
     */
    static MonotonicClock system() {
        return System::nanoTime;
    }
}
