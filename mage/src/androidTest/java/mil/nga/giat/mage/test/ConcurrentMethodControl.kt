package mil.nga.giat.mage.test

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

class ConcurrentMethodControl(private val timeoutMillis: Long = 1000L) {

    private val lock = ReentrantLock(true)
    private val cond = lock.newCondition()
    private val blocked = AtomicBoolean(false)

    fun enterAndAwaitRelease() {
        lock.tryLock(timeoutMillis, TimeUnit.MILLISECONDS)
        try {
            blocked.set(true)
            while (blocked.get()) {
                cond.signalAll()
                cond.await()
            }
        }
        finally {
            lock.unlock()
        }
    }

    fun waitUntilBlocked() {
        lock.tryLock(timeoutMillis, TimeUnit.MILLISECONDS)
        try {
            while (!blocked.get()) {
                cond.await()
            }
        }
        finally {
            lock.unlock()
        }
    }

    fun release() {
        try {
            lock.tryLock(timeoutMillis, TimeUnit.MILLISECONDS)
            blocked.set(false)
            cond.signalAll()
        }
        finally {
            lock.unlock()
        }
    }
}