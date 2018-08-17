package mil.nga.giat.mage.data

import android.os.AsyncTask
import android.support.test.annotation.UiThreadTest
import com.nhaarman.mockitokotlin2.*
import mil.nga.giat.mage.test.AsyncTesting
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

class UniqueAsyncTaskManagerTest {

    lateinit var listener: UniqueAsyncTaskManager.TaskListener<String, String>
    lateinit var executor: Executor
    lateinit var realExecutor: ThreadPoolExecutor

    @Rule
    @JvmField
    val testName = TestName()

    @Rule
    @JvmField
    val onMainThread = AsyncTesting.MainLooperAssertion()

    private val testTimeout = 300000L

    @Before
    fun setup() {
        listener = mock()
        executor = mock {
            on { execute(any()) }.then { invoc ->
                realExecutor.execute(invoc.arguments[0] as Runnable)
            }
        }
        val threadCount = AtomicInteger(0)
        realExecutor = ThreadPoolExecutor(4, 4, testTimeout, TimeUnit.MILLISECONDS, LinkedBlockingQueue(), ThreadFactory { target ->
            Thread(target, "${javaClass.simpleName}_pool#${threadCount.incrementAndGet()}")
        })
    }

    @After
    fun waitForThreadPoolTermination() {
        realExecutor.shutdown()
        realExecutor.awaitTermination(testTimeout, TimeUnit.MILLISECONDS)
    }

    @Test
    @UiThreadTest
    fun taskRunsImmediatelyWhenNoOtherTasksAreRunningOrWaiting() {

        val executor = mock<Executor> {
            on { execute(any()) }.then { invoc -> (invoc.arguments[0] as Runnable).run() }
        }
        val callable = mock<Callable<String>>()
        val task = ImmediateFuture(callable)
        val manager = UniqueAsyncTaskManager(listener, executor)
        manager.execute(testName.methodName, task)

        verify(executor).execute(any())
        verify(callable).call()
    }

    @Test
    fun doesNotExecutePendingTaskUntilCurrentTaskFinishes() {

        val callLock = ReentrantLock()
        val callCondition = callLock.newCondition()
        val callBlocked = AtomicBoolean(false)
        val task1 = FutureTask<String> {
            callLock.lock()
            callBlocked.set(true)
            while (callBlocked.get()) {
                callCondition.signalAll()
                callCondition.await(testTimeout, TimeUnit.MILLISECONDS)
            }
            callLock.unlock()
            "first done"
        }
        val manager = UniqueAsyncTaskManager(listener, executor)
        manager.execute(testName.methodName, task1)

        verify(executor).execute(any())

        callLock.lock()
        while (!callBlocked.get()) {
            callCondition.await(testTimeout, TimeUnit.MILLISECONDS)
        }
        callLock.unlock()

        val secondDone = AtomicBoolean(false)
        val task2 = FutureTask<String> {
            secondDone.set(true)
            "second done"
        }
        manager.execute(testName.methodName, task2)

        verifyNoMoreInteractions(executor)

        callLock.lock()
        callBlocked.set(false)
        callCondition.signalAll()
        callLock.unlock()

        onMainThread.assertThatWithin(testTimeout, secondDone::get, equalTo(true))

        waitForThreadPoolTermination()

        verify(executor, times(2)).execute(any())
    }

    @Test
    fun doesNotExecutePendingTaskWhenAnotherTaskPreemptsBeforeCurrentTaskFinishes() {

        val callLock = ReentrantLock()
        val callCondition = callLock.newCondition()
        val callBlocked = AtomicBoolean(false)
        val task1 = FutureTask<String> {
            callLock.lock()
            callBlocked.set(true)
            while (callBlocked.get()) {
                callCondition.signalAll()
                callCondition.await(testTimeout, TimeUnit.MILLISECONDS)
            }
            callLock.unlock()
            "first done"
        }
        val manager = UniqueAsyncTaskManager(listener, executor)
        manager.execute(testName.methodName, task1)

        verify(executor).execute(any())

        callLock.lock()
        while (!callBlocked.get()) {
            callCondition.await(testTimeout, TimeUnit.MILLISECONDS)
        }
        callLock.unlock()

        val preempted = mock<RunnableFuture<String>> {
            on { get() }.thenReturn("erroneous call")
        }
        manager.execute(testName.methodName, preempted)

        verifyNoMoreInteractions(executor)

        val preemptorDone = AtomicBoolean(false)
        val preemptor = FutureTask {
            preemptorDone.set(true)
            "preemptor done"
        }

        manager.execute(testName.methodName, preemptor)

        verifyNoMoreInteractions(executor)

        callLock.lock()
        callBlocked.set(false)
        callCondition.signalAll()
        callLock.unlock()

        onMainThread.assertThatWithin(testTimeout, preemptorDone::get, equalTo(true))

        waitForThreadPoolTermination()

        verify(executor, times(2)).execute(any())
        verify(preempted).cancel(false)
        verifyNoMoreInteractions(preempted)
    }

    @Test
    fun executesTasksWithDifferentKeysConcurrently() {
        fail("unimplemented")
    }

    @Test
    fun notifiesListenerWhenRunningTaskIsCancelled() {
        fail("unimplemented")
    }

    @Test
    fun notifiesListenerWhenPendingTaskIsPreempted() {
        fail("unimplemented")
    }

    class ImmediateFuture<V>(private val call: Callable<V>) : RunnableFuture<V> {

        private var result: V? = null
        private var called = false

        override fun run() {
            result = call.call()
            called = true
        }


        override fun isDone(): Boolean {
            return called
        }

        override fun get(): V? {
            return result
        }

        override fun get(timeout: Long, unit: TimeUnit?): V? {
            return get()
        }

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            return false
        }

        override fun isCancelled(): Boolean {
            return false
        }
    }
}