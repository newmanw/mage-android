package mil.nga.giat.mage.data

import android.os.Looper
import android.support.test.annotation.UiThreadTest
import com.nhaarman.mockitokotlin2.*
import mil.nga.giat.mage.test.AsyncTesting
import mil.nga.giat.mage.test.AsyncTesting.waitForMainThreadToRun
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

class UniqueAsyncTaskManagerTest {

    private lateinit var listener: UniqueAsyncTaskManager.TaskListener<String, String>
    private lateinit var executor: Executor
    private lateinit var realExecutor: ThreadPoolExecutor

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

    @Test(expected = Error::class)
    fun throwsExceptionWhenSubmittingTasksOffMainThread() {

        val manager = UniqueAsyncTaskManager(listener, mock())
        manager.execute("fail", mock())
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
        waitForMainThreadToRun { manager.execute(testName.methodName, task1) }

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
        waitForMainThreadToRun { manager.execute(testName.methodName, task2) }

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
        waitForMainThreadToRun { manager.execute(testName.methodName, task1) }

        verify(executor).execute(any())

        callLock.lock()
        while (!callBlocked.get()) {
            callCondition.await(testTimeout, TimeUnit.MILLISECONDS)
        }
        callLock.unlock()

        val preempted = mock<RunnableFuture<String>> {
            on { get() }.thenReturn("erroneous call")
        }
        waitForMainThreadToRun { manager.execute(testName.methodName, preempted) }

        verifyNoMoreInteractions(executor)

        val preemptorDone = AtomicBoolean(false)
        val preemptor = FutureTask {
            preemptorDone.set(true)
            "preemptor done"
        }

        waitForMainThreadToRun { manager.execute(testName.methodName, preemptor) }

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

        val manager = UniqueAsyncTaskManager(listener, executor)
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
        waitForMainThreadToRun { manager.execute("${testName.methodName}-1", task1) }

        callLock.lock()
        while (!callBlocked.get()) {
            callCondition.await(testTimeout, TimeUnit.MILLISECONDS)
        }
        callLock.unlock()

        val task2 = mock<RunnableFuture<String>> {
            on { get() }.thenReturn("second done")
        }
        waitForMainThreadToRun { manager.execute("${testName.methodName}-2", task2) }

        verify(executor, times(2)).execute(any())

        val inOrder = Mockito.inOrder(task2)
        inOrder.verify(task2, timeout(testTimeout)).run()
        inOrder.verify(task2, timeout(testTimeout)).get()

        callLock.lock()
        callBlocked.set(false)
        callCondition.signalAll()
        callLock.unlock()
    }

    @Test
    fun notifiesListenerOnMainThreadWhenCurrentTaskFinishesNormally() {

        val manager = UniqueAsyncTaskManager(listener, executor)
        val task = FutureTask { testName.methodName.reversed() }
        val notifiedOnMainThread = AtomicBoolean(false)
        whenever(listener.taskFinished(testName.methodName, task)).then { _ ->
            notifiedOnMainThread.set(Looper.getMainLooper() === Looper.myLooper())
        }

        waitForMainThreadToRun { manager.execute(testName.methodName, task) }

        onMainThread.assertThatWithin(testTimeout, notifiedOnMainThread::get, equalTo(true))
    }

    @Test
    fun notifiesListenerOnMainThreadWhenRunningTaskIsCancelled() {

        val taskLock = ReentrantLock(true)
        val taskBlocked = AtomicBoolean(false)
        val taskCondition = taskLock.newCondition()
        val manager = UniqueAsyncTaskManager(listener, executor)
        val cancelledTask = FutureTask {
            taskLock.lock()
            taskBlocked.set(true)
            while (taskBlocked.get()) {
                taskCondition.signalAll()
                taskCondition.await(testTimeout, TimeUnit.MILLISECONDS)
            }
            taskLock.unlock()
            testName.methodName
        }
        val finishedTask = FutureTask { testName.methodName.reversed() }
        val cancelledOnMainThread = AtomicBoolean(false)
        val finishedOnMainThread = AtomicBoolean(false)
        whenever(listener.taskCancelled(testName.methodName, cancelledTask)).then { _ ->
            cancelledOnMainThread.set(Looper.getMainLooper() === Looper.myLooper())
        }
        whenever(listener.taskFinished(testName.methodName, finishedTask)).then { _ ->
            finishedOnMainThread.set(Looper.getMainLooper() === Looper.myLooper())
        }

        waitForMainThreadToRun { manager.execute(testName.methodName, cancelledTask) }

        taskLock.lock()
        while (!taskBlocked.get()) {
            taskCondition.await(testTimeout, TimeUnit.MILLISECONDS)
        }
        taskLock.unlock()

        waitForMainThreadToRun { manager.execute(testName.methodName, finishedTask) }

        taskLock.lock()
        taskBlocked.set(false)
        taskCondition.signalAll()
        taskLock.unlock()

        onMainThread.assertThatWithin(testTimeout, finishedOnMainThread::get, equalTo(true))

        val order = inOrder(listener)
        order.verify(listener).taskCancelled(testName.methodName, cancelledTask)
        order.verify(listener).taskFinished(testName.methodName, finishedTask)
        assertTrue(cancelledOnMainThread.get())
    }

    @Test
    fun notifiesListenerOnMainThreadWhenPendingTaskIsPreempted() {

        val taskLock = ReentrantLock(true)
        val taskBlocked = AtomicBoolean(false)
        val taskCondition = taskLock.newCondition()
        val manager = UniqueAsyncTaskManager(listener, executor)
        val cancelledTask = FutureTask {
            taskLock.lock()
            taskBlocked.set(true)
            while (taskBlocked.get()) {
                taskCondition.signalAll()
                taskCondition.await(testTimeout, TimeUnit.MILLISECONDS)
            }
            taskLock.unlock()
            testName.methodName
        }
        val preemptedTask = FutureTask { "${testName.methodName}.preempted" }
        val finishedTask = FutureTask { testName.methodName.reversed() }
        val cancelledOnMainThread = AtomicBoolean(false)
        val preemptedOnMainThread = AtomicBoolean(false)
        val finishedOnMainThread = AtomicBoolean(false)
        whenever(listener.taskCancelled(testName.methodName, cancelledTask)).then { _ ->
            cancelledOnMainThread.set(Looper.getMainLooper() === Looper.myLooper())
        }
        whenever(listener.taskPreempted(testName.methodName, preemptedTask)).then { _ ->
            preemptedOnMainThread.set(Looper.getMainLooper() === Looper.myLooper())
        }
        whenever(listener.taskFinished(testName.methodName, finishedTask)).then { _ ->
            finishedOnMainThread.set(Looper.getMainLooper() === Looper.myLooper())
        }

        waitForMainThreadToRun { manager.execute(testName.methodName, cancelledTask) }

        taskLock.lock()
        while (!taskBlocked.get()) {
            taskCondition.await(testTimeout, TimeUnit.MILLISECONDS)
        }
        taskLock.unlock()

        waitForMainThreadToRun { manager.execute(testName.methodName, preemptedTask) }
        waitForMainThreadToRun { manager.execute(testName.methodName, finishedTask) }

        taskLock.lock()
        taskBlocked.set(false)
        taskCondition.signalAll()
        taskLock.unlock()

        onMainThread.assertThatWithin(testTimeout, finishedOnMainThread::get, equalTo(true))

        val order = inOrder(listener)
        // TODO: the order of preempted vs. cancelled is kind of strange but right now it's based on when the underlying async task finishes
        order.verify(listener).taskPreempted(testName.methodName, preemptedTask)
        order.verify(listener).taskCancelled(testName.methodName, cancelledTask)
        order.verify(listener).taskFinished(testName.methodName, finishedTask)
        assertTrue(preemptedOnMainThread.get())
        assertTrue(cancelledOnMainThread.get())
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