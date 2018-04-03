package mil.nga.giat.mage.test;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Assert;
import org.junit.rules.ExternalResource;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;

public class AsyncTesting {

    public static void waitForMainThreadToRun(Runnable task) {
        FutureTask<Void> blockUntilRun = new FutureTask<>(task, null);
        new Handler(Looper.getMainLooper()).post(blockUntilRun);
        try {
            blockUntilRun.get();
        }
        catch (Throwable t) {
            throw new Error("error waiting for main thread task to complete", t);
        }
    }

    public static class EventuallyMatcher<T> extends TypeSafeMatcher<T> {
        private static Duration defaultTimeout = Duration.ofMillis(5000);
        private final Matcher<T> matcher;
        private final Duration timeout;

        private EventuallyMatcher(Matcher<T> matcher) {
            this(matcher, defaultTimeout);
        }

        private EventuallyMatcher(Matcher<T> matcher, Duration timeout) {
            this.matcher = matcher;
            this.timeout = timeout;
        }

        public static <T> EventuallyMatcher<T> eventually(Matcher<T> matcher) {
            return new EventuallyMatcher<T>(matcher);
        }

        public static <T> EventuallyMatcher<T> eventually(Matcher<T> matcher, Duration timeout) {
            return new EventuallyMatcher<T>(matcher, timeout);
        }

        @Override
        protected boolean matchesSafely(T item) {
            Instant start = Instant.now();
            while(Duration.between(start, Instant.now()).compareTo(timeout) < 0) {
                if(matcher.matches(item)) return true;
            }
            return false;
        }

        @Override
        public void describeTo(Description description) {
            description.appendDescriptionOf(matcher);
        }

        @Override
        public void describeMismatchSafely(T item, Description mismatchDescription) {
            mismatchDescription.appendText(item.toString());
        }
    }

    public static class MainLooperAssertion extends ExternalResource {

        private static final int CHECK_MATCH = 0x1234;

        private Handler assertionHandler;
        private Handler.Callback callback;

        public <T> void assertOnMainThreadThatWithin(long timeout, Supplier<T> actual, Matcher<T> matcher) throws InterruptedException {
            TimedAssertion<T> assertion = new TimedAssertion<>(timeout, matcher, actual);
            Message matchMessage = assertionHandler.obtainMessage(CHECK_MATCH, assertion);
            assertionHandler.sendMessage(matchMessage);
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
