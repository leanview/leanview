package com.liskovsoft.smartyoutubetv2.common.leanview;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * leanview: how many backup workers (Drive or local) are writing their files right now. The wedge guard does not end the
 * process while one runs: an exit in the middle of a Drive upload leaves part of the files new and part old, and a later restore
 * mixes them (final review, M3). The workers call {@link #started()} and, in a finally block, {@link #finished()}.
 */
public final class RunningBackups {
    private static final AtomicInteger sRunning = new AtomicInteger();

    private RunningBackups() {
    }

    public static void started() {
        sRunning.incrementAndGet();
    }

    public static void finished() {
        int now;
        do {
            now = sRunning.get();
        } while (now > 0 && !sRunning.compareAndSet(now, now - 1));
    }

    public static boolean any() {
        return sRunning.get() > 0;
    }
}
