package mil.nga.giat.mage.test;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import org.hamcrest.Matcher;
import org.junit.Assert;
import org.junit.rules.ExternalResource;

import java.util.concurrent.FutureTask;
import java.util.function.Supplier;

public class AsyncTesting {

    /**
     * Run the given task on the main thread, blocking the calling thread until the task returns.
     * If the calling thread is the main thread, simply run the given task and return.
     * @param task a {@link Runnable} task
     */
    public static void waitForMainThreadToRun(Runnable task) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run();
            return;
        }
        FutureTask<Void> blockUntilRun = new FutureTask<>(task, null);
        new Handler(Looper.getMainLooper()).post(blockUntilRun);
        try {
            blockUntilRun.get();
        }
        catch (Throwable t) {
            throw new Error("error waiting for main thread task to complete", t);
        }
    }

    /**
     * MainLooperAssertion is a JUnit {@link org.junit.Rule} that an asynchronous test can
     * use to {@link Assert#assertThat(Object, Matcher) assert} a value on Android's
     * {@link Looper#getMainLooper() main looper}.  This is useful when an asynchronous
     * test, without the the {@link android.support.test.annotation.UiThreadTest annotation}
     * needs to assert a value that should only be accessed on the main thread.  This
     * class will not lock the main thread in a tight loop, because it uses Android's
     * {@link Looper} message-queuing mechanism to continually check whether the actual
     * value matches the expected condition.  This allows other main thread code under test
     * to run while the test thread waits for the actual value to materialize on the main
     * thread.  The calling test thread will block/wait until the assertion passes or the
     * timeout expires.
     */
    public static class MainLooperAssertion extends ExternalResource {

        private static final int CHECK_MATCH = 0x1234;

        private Handler assertionHandler;
        private Handler.Callback callback;

        /**
         * Block the calling thread until the given assertion either matches or the given timeout passes.
         * @param timeout timeout in milliseconds
         * @param actual a {@link Supplier} for the actual value, which can be a lambda or method reference
         * @param matcher the matcher to apply to the actual value
         * @param <T> the type of value to match
         * @throws InterruptedException if the calling thread gets interrupted while waiting for the assertion
         */
        public <T> void assertThatWithin(long timeout, Supplier<T> actual, Matcher<T> matcher) throws InterruptedException {
            TimedAssertion<T> assertion = new TimedAssertion<>(timeout, matcher, actual);
            Message matchMessage = assertionHandler.obtainMessage(CHECK_MATCH, assertion);
            assertionHandler.sendMessage(matchMessage);
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (assertion) {
                while (assertion.isTesting()) {
                    assertion.wait(timeout);
                }
            }
            Assert.assertThat(assertion.lastSuppliedActual, matcher);
        }

        @Override
        protected void before() throws Throwable {
            callback = msg -> {
                if (msg.what == CHECK_MATCH && msg.obj instanceof TimedAssertion) {
                    TimedAssertion<?> assertion = (TimedAssertion<?>) msg.obj;
                    if (assertion.check()) {
                        assertionHandler.sendMessage(assertionHandler.obtainMessage(CHECK_MATCH, assertion));
                    }
                    return true;
                }
                return false;
            };
            assertionHandler = new Handler(Looper.getMainLooper(), callback);
        }

        @Override
        protected void after() {
            assertionHandler.removeMessages(CHECK_MATCH);
            assertionHandler = null;
        }
    }

    private static class TimedAssertion<T> {

        private final long expiration;
        private final Matcher<T> matcher;
        private final Supplier<T> actual;
        private boolean matched = false;
        private boolean expired = false;
        private T lastSuppliedActual;

        private TimedAssertion(long timeout, Matcher<T> matcher, Supplier<T> actual) {
            expiration = System.currentTimeMillis() + timeout;
            this.matcher = matcher;
            this.actual = actual;
        }

        private synchronized boolean check() {
            if (System.currentTimeMillis() > expiration) {
                expired = true;
            }
            lastSuppliedActual = actual.get();
            if (matcher.matches(lastSuppliedActual)) {
                matched = true;
            }
            boolean isTesting = isTesting();
            notifyAll();
            return isTesting;
        }

        private synchronized boolean isTesting() {
            return !expired && !matched;
        }
    }
}
