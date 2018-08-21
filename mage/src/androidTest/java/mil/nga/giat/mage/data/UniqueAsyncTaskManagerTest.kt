package mil.nga.giat.mage.data

import android.os.Looper
import android.support.test.annotation.UiThreadTest
import com.nhaarman.mockitokotlin2.*
import mil.nga.giat.mage.data.UniqueAsyncTaskManager.Task
import mil.nga.giat.mage.test.AsyncTesting
import mil.nga.giat.mage.test.AsyncTesting.waitForMainThreadToRun
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.lang.Integer.sum
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.exp

class UniqueAsyncTaskManagerTest {

    private lateinit var listener: UniqueAsyncTaskManager.TaskListener<String, Int, String>
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
        manager.execute("fail", mock<Task<Int, String>>())
    }

    @Test
    @UiThreadTest
    fun taskRunsImmediatelyWhenNoOtherTasksAreRunningOrWaiting() {

        val executor = mock<Executor> {
            on { execute(any()) }.then { invoc -> (invoc.arguments[0] as Runnable).run() }
        }
        val task = mock<Task<Int, String>>()
        val manager = UniqueAsyncTaskManager(listener, executor)
        manager.execute(testName.methodName, task)

        verify(executor).execute(any())
        verify(task).run(any())
    }

    @Test
    fun doesNotExecutePendingTaskUntilCurrentTaskFinishes() {

        val callLock = ReentrantLock()
        val callCondition = callLock.newCondition()
        val callBlocked = AtomicBoolean(false)
        val manager = UniqueAsyncTaskManager(listener, executor)
        waitForMainThreadToRun {
            manager.execute(testName.methodName) {
                callLock.lock()
                callBlocked.set(true)
                while (callBlocked.get()) {
                    callCondition.signalAll()
                    callCondition.await(testTimeout, TimeUnit.MILLISECONDS)
                }
                callLock.unlock()
                "first done"
            }
        }

        verify(executor).execute(any())

        callLock.lock()
        while (!callBlocked.get()) {
            callCondition.await(testTimeout, TimeUnit.MILLISECONDS)
        }
        callLock.unlock()

        val secondDone = AtomicBoolean(false)
        waitForMainThreadToRun {
            manager.execute(testName.methodName) {
                secondDone.set(true)
                "second done"
            }
        }

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
        val manager = UniqueAsyncTaskManager(listener, executor)
        waitForMainThreadToRun {
            manager.execute(testName.methodName) {
                callLock.lock()
                callBlocked.set(true)
                while (callBlocked.get()) {
                    callCondition.signalAll()
                    callCondition.await(testTimeout, TimeUnit.MILLISECONDS)
                }
                callLock.unlock()
                "first done"
            }
        }

        verify(executor).execute(any())

        callLock.lock()
        while (!callBlocked.get()) {
            callCondition.await(testTimeout, TimeUnit.MILLISECONDS)
        }
        callLock.unlock()

        val preempted = mock<Task<Int, String>> {
            on { run(any()) }.thenReturn("erroneous call")
        }
        waitForMainThreadToRun { manager.execute(testName.methodName, preempted) }

        verifyNoMoreInteractions(executor)

        val preemptorDone = AtomicBoolean(false)

        waitForMainThreadToRun {
            manager.execute(testName.methodName) {
                preemptorDone.set(true)
                "preemptor done"
            }
        }

        verifyNoMoreInteractions(executor)

        callLock.lock()
        callBlocked.set(false)
        callCondition.signalAll()
        callLock.unlock()

        onMainThread.assertThatWithin(testTimeout, preemptorDone::get, equalTo(true))

        waitForThreadPoolTermination()

        verify(executor, times(2)).execute(any())
        verifyNoMoreInteractions(preempted)
    }

    @Test
    fun executesTasksWithDifferentKeysConcurrently() {

        val manager = UniqueAsyncTaskManager(listener, executor)
        val callLock = ReentrantLock()
        val callCondition = callLock.newCondition()
        val callBlocked = AtomicBoolean(false)
        waitForMainThreadToRun {
            manager.execute("${testName.methodName}-1") {
                callLock.lock()
                callBlocked.set(true)
                while (callBlocked.get()) {
                    callCondition.signalAll()
                    callCondition.await(testTimeout, TimeUnit.MILLISECONDS)
                }
                callLock.unlock()
                "first done"
            }
        }

        callLock.lock()
        while (!callBlocked.get()) {
            callCondition.await(testTimeout, TimeUnit.MILLISECONDS)
        }
        callLock.unlock()

        val task2 = mock<Task<Int, String>> {
            on { run(any()) }.thenReturn("second done")
        }
        waitForMainThreadToRun { manager.execute("${testName.methodName}-2", task2) }

        verify(executor, times(2)).execute(any())
        verify(task2, timeout(testTimeout)).run(any())

        callLock.lock()
        callBlocked.set(false)
        callCondition.signalAll()
        callLock.unlock()
    }

    @Test
    fun notifiesListenerOnMainThreadWhenCurrentTaskFinishesNormally() {

        val manager = UniqueAsyncTaskManager(listener, executor)
        val task = object : Task<Int, String> {
            override fun run(support: UniqueAsyncTaskManager.TaskSupport<Int>): String {
                return testName.methodName.reversed()
            }
        }
        val notifiedOnMainThread = AtomicBoolean(false)
        whenever(listener.taskFinished(eq(testName.methodName), same(task), any())).then { _ ->
            notifiedOnMainThread.set(Looper.getMainLooper() === Looper.myLooper())
        }

        waitForMainThreadToRun { manager.execute(testName.methodName, task) }

        onMainThread.assertThatWithin(testTimeout, notifiedOnMainThread::get, equalTo(true))

        verify(listener).taskFinished(eq(testName.methodName), same(task), eq(testName.methodName.reversed()))
    }

    @Test
    fun notifiesListenerOnMainThreadWhenRunningTaskIsCancelled() {

        val taskLock = ReentrantLock(true)
        val taskBlocked = AtomicBoolean(false)
        val taskCondition = taskLock.newCondition()
        val manager = UniqueAsyncTaskManager(listener, executor)
        val cancelledFlag = AtomicBoolean(false)
        val cancelledTask = object : Task<Int, String> {
            override fun run(support: UniqueAsyncTaskManager.TaskSupport<Int>): String {
                taskLock.lock()
                taskBlocked.set(true)
                while (taskBlocked.get()) {
                    taskCondition.signalAll()
                    taskCondition.await(testTimeout, TimeUnit.MILLISECONDS)
                }
                taskLock.unlock()
                cancelledFlag.set(support.isCancelled())
                return testName.methodName
            }
        }
        val finishedTask = object : Task<Int, String> {
            override fun run(support: UniqueAsyncTaskManager.TaskSupport<Int>): String {
                return testName.methodName.reversed()
            }
        }
        val cancelledOnMainThread = AtomicBoolean(false)
        val finishedOnMainThread = AtomicBoolean(false)
        whenever(listener.taskCancelled(testName.methodName, cancelledTask)).then { _ ->
            cancelledOnMainThread.set(Looper.getMainLooper() === Looper.myLooper())
        }
        whenever(listener.taskFinished(eq(testName.methodName), same(finishedTask), any())).then { _ ->
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
        order.verify(listener).taskFinished(testName.methodName, finishedTask, testName.methodName.reversed())
        assertTrue(cancelledOnMainThread.get())
        assertTrue(cancelledFlag.get())
    }

    @Test
    fun notifiesListenerOnMainThreadWhenPendingTaskIsPreempted() {

        val taskLock = ReentrantLock(true)
        val taskBlocked = AtomicBoolean(false)
        val taskCondition = taskLock.newCondition()
        val manager = UniqueAsyncTaskManager(listener, executor)
        val cancelledTask = object : Task<Int, String> {
            override fun run(support: UniqueAsyncTaskManager.TaskSupport<Int>): String {
                taskLock.lock()
                taskBlocked.set(true)
                while (taskBlocked.get()) {
                    taskCondition.signalAll()
                    taskCondition.await(testTimeout, TimeUnit.MILLISECONDS)
                }
                taskLock.unlock()
                return testName.methodName
            }
        }
        val preemptedTask = object : Task<Int, String> {
            override fun run(support: UniqueAsyncTaskManager.TaskSupport<Int>): String {
                return "${testName.methodName}.preempted"
            }
        }
        val finishedTask = object : Task<Int, String> {
            override fun run(support: UniqueAsyncTaskManager.TaskSupport<Int>): String {
                return testName.methodName.reversed()
            }
        }
        val cancelledOnMainThread = AtomicBoolean(false)
        val preemptedOnMainThread = AtomicBoolean(false)
        val finishedOnMainThread = AtomicBoolean(false)
        whenever(listener.taskCancelled(testName.methodName, cancelledTask)).then { _ ->
            cancelledOnMainThread.set(Looper.getMainLooper() === Looper.myLooper())
        }
        whenever(listener.taskPreempted(testName.methodName, preemptedTask)).then { _ ->
            preemptedOnMainThread.set(Looper.getMainLooper() === Looper.myLooper())
        }
        whenever(listener.taskFinished(eq(testName.methodName), same(finishedTask), any())).then { _ ->
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
        order.verify(listener).taskFinished(testName.methodName, finishedTask, testName.methodName.reversed())
        assertTrue(preemptedOnMainThread.get())
        assertTrue(cancelledOnMainThread.get())
    }

    @Test
    fun notifiesListenerOnMainThreadWhenTaskReportsProgress() {

        val manager = UniqueAsyncTaskManager(listener, executor)
        val task = object : Task<Int, String> {
            override fun run(support: UniqueAsyncTaskManager.TaskSupport<Int>): String {
                for (progress in 1..5) {
                    support.reportProgressToMainThread(progress)
                }
                return testName.methodName.reversed()
            }
        }
        val progressOnMainThread = AtomicInteger(0)
        whenever(listener.taskProgress(eq(testName.methodName), same(task), any())).then { invoc ->
            val progress = invoc.arguments[2] as Int
            if (Looper.getMainLooper() === Looper.myLooper()) {
                progressOnMainThread.addAndGet(progress)
            }
            null
        }

        waitForMainThreadToRun { manager.execute(testName.methodName, task) }

        verify(listener, timeout(testTimeout)).taskFinished(testName.methodName, task, testName.methodName.reversed())
        assertThat(progressOnMainThread.get(), equalTo((1..5).sum()))
    }

    @Test
    fun reflectsCorrectRunningAndPendingTasks() {

        val taskLock = ReentrantLock(true)
        val taskBlocked = AtomicBoolean(false)
        val taskCondition = taskLock.newCondition()
        val manager = UniqueAsyncTaskManager(listener, executor)
        val task1 = object : Task<Int, String> {
            override fun run(support: UniqueAsyncTaskManager.TaskSupport<Int>): String? {
                return testName.methodName
            }
        }
        val task2 = object :Task<Int, String> {
            override fun run(support: UniqueAsyncTaskManager.TaskSupport<Int>): String? {
                taskLock.lock()
                taskBlocked.set(true)
                while (taskBlocked.get()){
                    taskCondition.signalAll()
                    taskCondition.await()
                }
                taskLock.unlock()
                return testName.methodName.reversed()
            }
        }

        waitForMainThreadToRun {
            manager.execute(testName.methodName, task1)

            assertTrue(manager.isRunningTaskForKey(testName.methodName))
            assertThat(manager.currentTaskForKey(testName.methodName)!!, sameInstance<Task<Int, String>>(task1))
            assertThat(manager.pendingTaskForKey(testName.methodName), nullValue())

            manager.execute(testName.methodName, task2)

            assertTrue(manager.isRunningTaskForKey(testName.methodName))
            assertThat(manager.currentTaskForKey(testName.methodName), sameInstance<Task<Int, String>>(task1))
            assertThat(manager.pendingTaskForKey(testName.methodName), sameInstance<Task<Int, String>>(task2))
        }

        verify(listener, timeout(testTimeout)).taskCancelled(testName.methodName, task1)

        taskLock.lock()
        while (!taskBlocked.get()) {
            taskCondition.await()
        }
        taskLock.unlock()

        assertTrue(manager.isRunningTaskForKey(testName.methodName))
        assertThat(manager.currentTaskForKey(testName.methodName), sameInstance<Task<Int, String>>(task2))
        assertThat(manager.pendingTaskForKey(testName.methodName), nullValue())

        taskLock.lock()
        taskBlocked.set(false)
        taskCondition.signalAll()
        taskLock.unlock()

        onMainThread.assertThatWithin(testTimeout, { manager.currentTaskForKey(testName.methodName) }, nullValue())

        assertFalse(manager.isRunningTaskForKey(testName.methodName))
        assertThat(manager.pendingTaskForKey(testName.methodName), nullValue())
    }

    @Test
    fun cancelledTaskIsCurrentTaskInCancelNotification() {

        val manager = UniqueAsyncTaskManager(listener, executor)
        val task1 = object : Task<Int, String> {
            override fun run(support: UniqueAsyncTaskManager.TaskSupport<Int>): String? {
                return testName.methodName
            }
        }
        val task2 = object : Task<Int, String> {
            override fun run(support: UniqueAsyncTaskManager.TaskSupport<Int>): String? {
                return testName.methodName.reversed()
            }
        }
        whenever(listener.taskCancelled(testName.methodName, task1)).then {
            assertThat(manager.currentTaskForKey(testName.methodName), sameInstance<Task<Int, String>>(task1))
        }

        waitForMainThreadToRun {
            manager.execute(testName.methodName, task1)
            manager.execute(testName.methodName, task2)
        }

        onMainThread.assertThatWithin(testTimeout, { manager.isRunningTaskForKey(testName.methodName) }, equalTo(false))

        verify(listener, timeout(testTimeout)).taskFinished(testName.methodName, task2, testName.methodName.reversed())
    }

    @Test
    fun finishedTaskIsCurrentTaskInFinishNotification() {

        val manager = UniqueAsyncTaskManager(listener, executor)
        val task1 = object : Task<Int, String> {
            override fun run(support: UniqueAsyncTaskManager.TaskSupport<Int>): String? {
                return testName.methodName.reversed()
            }
        }
        val finishedWasCurrent = AtomicBoolean(false)
        whenever(listener.taskFinished(testName.methodName, task1, testName.methodName.reversed())).then {
            finishedWasCurrent.set(manager.currentTaskForKey(testName.methodName) === task1)
        }

        waitForMainThreadToRun { manager.execute(testName.methodName, task1) }

        onMainThread.assertThatWithin(testTimeout, finishedWasCurrent::get, equalTo(true))

        verify(listener, timeout(testTimeout)).taskFinished(testName.methodName, task1, testName.methodName.reversed())
    }

    @Test
    fun preemptingTaskIsPendingTaskInPreemptNotification() {

        val manager = UniqueAsyncTaskManager(listener, executor)
        val taskLock = ReentrantLock(true)
        val taskBlocked = AtomicBoolean(false)
        val taskCondition = taskLock.newCondition()
        val task1 = object : Task<Int, String> {
            override fun run(support: UniqueAsyncTaskManager.TaskSupport<Int>): String? {
                taskLock.lock()
                taskBlocked.set(true)
                while (taskBlocked.get()) {
                    taskCondition.signalAll()
                    taskCondition.await()
                }
                taskLock.unlock()
                return testName.methodName
            }
        }
        val task2 = object : Task<Int, String> {
            override fun run(support: UniqueAsyncTaskManager.TaskSupport<Int>): String? {
                throw Error("preempted task should not run")
            }
        }
        val task3 = object : Task<Int, String> {
            override fun run(support: UniqueAsyncTaskManager.TaskSupport<Int>): String? {
                return testName.methodName.reversed()
            }
        }
        val preemptingWasPending = AtomicBoolean(false)
        whenever(listener.taskPreempted(testName.methodName, task2)).then {
            preemptingWasPending.set(manager.pendingTaskForKey(testName.methodName) === task3)
        }

        waitForMainThreadToRun { manager.execute(testName.methodName, task1) }

        taskLock.lock()
        while (!taskBlocked.get()) {
            taskCondition.await()
        }
        taskLock.unlock()

        waitForMainThreadToRun {
            manager.execute(testName.methodName, task2)
            manager.execute(testName.methodName, task3)
        }

        taskLock.lock()
        taskBlocked.set(false)
        taskCondition.signalAll()
        taskLock.unlock()

        onMainThread.assertThatWithin(testTimeout, { manager.isRunningTaskForKey(testName.methodName) }, equalTo(false))

        val order = inOrder(listener)
        order.verify(listener).taskPreempted(testName.methodName, task2)
        order.verify(listener).taskCancelled(testName.methodName, task1)
        order.verify(listener).taskFinished(testName.methodName, task3, testName.methodName.reversed())
        assertTrue(preemptingWasPending.get())
    }

    @Test
    fun submittingTaskInsideCancelNotification() {
        fail("unimplemented")
    }

    @Test
    fun submittingTaskInsidePreemptNotification() {
        fail("unimplemented")
    }

    @Test
    fun submittingTaskInsideFinishNotification() {
        fail("unimplemented")
    }
}