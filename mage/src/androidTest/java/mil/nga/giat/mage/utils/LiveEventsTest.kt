package mil.nga.giat.mage.utils

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.LifecycleOwner
import android.arch.lifecycle.LifecycleRegistry
import mil.nga.giat.mage.test.AsyncTesting
import mil.nga.giat.mage.utils.LiveEvents.EventProtocol
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

class LiveEventsTest : LifecycleOwner {

    companion object {
        private const val EVENT_1 = 1
        private const val EVENT_2 = 2
    }

    private lateinit var lifecycle: LifecycleRegistry

    override fun getLifecycle(): Lifecycle {
        return lifecycle
    }

    interface TestListener {

        fun onEvent1()
        fun onEvent2(data: String)
    }

    private val testListenerProtocol = EventProtocol<TestListener> { what, listener, data ->
        when (what) {
            EVENT_1 -> listener.onEvent1()
            EVENT_2 -> {
                val str = data as String
                listener.onEvent2(str)
            }
            else -> throw IllegalArgumentException("invalid event type: $what")
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    val onMainThread = AsyncTesting.MainLooperAssertion()
        @Rule get

    @Before
    fun setup() {
        lifecycle = LifecycleRegistry(this)
    }

    @Test
    fun removesLifecycleOwnerListenersWhenOwnerIsDestroyed() {

        val refs = ReferenceQueue<Any>()

        lateinit var doomed: LifecycleRegistry

        val owner = WeakReference<LifecycleOwner>(object: LifecycleOwner {
            init {
                doomed = LifecycleRegistry(this)
                doomed.markState(Lifecycle.State.RESUMED)
            }
            override fun getLifecycle(): Lifecycle {
                return doomed
            }
        }, refs)

        val listener1 = WeakReference<TestListener>(object: TestListener {
            override fun onEvent1() {
            }
            override fun onEvent2(data: String) {
            }
        }, refs)

        val listener2 = WeakReference<TestListener>(object: TestListener {
            override fun onEvent1() {
            }
            override fun onEvent2(data: String) {
            }
        }, refs)

        val events = LiveEvents<TestListener>(testListenerProtocol)
        events.listen(owner.get(), listener1.get())
        events.listen(owner.get(), listener2.get())
        doomed.markState(Lifecycle.State.DESTROYED)

        onMainThread.assertThatWithin(5000, {
            Runtime.getRuntime().gc()
            owner.isEnqueued && listener1.isEnqueued && listener2.isEnqueued
        }, equalTo(true))
    }

    @Test
    fun sendsTheEventsWhenListenersAreActive() {

        val events = LiveEvents<TestListener>(testListenerProtocol)
        lifecycle.markState(Lifecycle.State.STARTED)
        val listener1 = mock(TestListener::class.java)
        val listener2 = mock(TestListener::class.java)
        events.listen(this, listener1)
        events.listen(this, listener2)
        events.trigger(EVENT_1)
        events.trigger(EVENT_2, "STARTED")

        val inOrder = Mockito.inOrder(listener1, listener2)
        inOrder.verify(listener1).onEvent1()
        inOrder.verify(listener2).onEvent1()
        inOrder.verify(listener1).onEvent2("STARTED")
        inOrder.verify(listener2).onEvent2("STARTED")

        lifecycle.markState(Lifecycle.State.RESUMED)
        events.trigger(EVENT_2, "RESUMED")
        events.trigger(EVENT_1)

        inOrder.verify(listener1).onEvent2("RESUMED")
        inOrder.verify(listener2).onEvent2("RESUMED")
        inOrder.verify(listener1).onEvent1()
        inOrder.verify(listener2).onEvent1()
    }

    @Test
    fun doesNotSendEventsToInactiveListeners() {
        fail("unimplemented")
    }
}