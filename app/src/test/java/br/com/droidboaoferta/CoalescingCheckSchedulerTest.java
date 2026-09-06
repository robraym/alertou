package br.com.droidboaoferta;

import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class CoalescingCheckSchedulerTest {
    @Test public void repeatedResumesDoNotQueueChecksAndExplicitRequestsCoalesce() throws Exception {
        CoalescingCheckScheduler scheduler = new CoalescingCheckScheduler();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondFinished = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        try {
            scheduler.start(() -> {
                maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                try {
                    if (calls.incrementAndGet() == 1) {
                        firstStarted.countDown();
                        await(releaseFirst);
                    } else secondFinished.countDown();
                } finally { active.decrementAndGet(); }
            }, TimeUnit.HOURS.toMillis(1));
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            for (int i = 0; i < 50; i++) scheduler.request(120_000);
            for (int i = 0; i < 50; i++) scheduler.request(0);
            releaseFirst.countDown();
            assertTrue(secondFinished.await(2, TimeUnit.SECONDS));
            assertEquals(2, calls.get());
            assertEquals(1, maximum.get());
        } finally { releaseFirst.countDown(); scheduler.stop(); }
    }

    @Test public void cancelledWorkerCannotPublishEvenIfNetworkClearsInterruption() throws Exception {
        CoalescingCheckScheduler scheduler = new CoalescingCheckScheduler();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        AtomicInteger published = new AtomicInteger();
        try {
            scheduler.start(() -> {
                started.countDown();
                try { new CountDownLatch(1).await(); }
                catch (InterruptedException ignored) { /* Simulates an HTTP client clearing interruption. */ }
                if (CoalescingCheckScheduler.isCurrentRun()) published.incrementAndGet();
                cancelled.countDown();
            }, TimeUnit.HOURS.toMillis(1));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            scheduler.stop();
            assertTrue(cancelled.await(2, TimeUnit.SECONDS));
            assertEquals(0, published.get());
        } finally { scheduler.stop(); }
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(2, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
}
