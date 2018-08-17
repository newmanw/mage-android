package mil.nga.giat.mage.data

import android.os.AsyncTask
import android.support.annotation.MainThread
import java.util.concurrent.CancellationException
import java.util.concurrent.Executor
import java.util.concurrent.Future
import java.util.concurrent.RunnableFuture

@MainThread
class UniqueAsyncTaskManager<Key, Result>(private val listener: TaskListener<Key, Result>, val executor: Executor = AsyncTask.THREAD_POOL_EXECUTOR) {

    private val tasks = HashMap<Key, TaskPair<Key, Result>>()
    private var disposed: Boolean = false

    fun execute(key: Key, task: RunnableFuture<Result>) {
        if (disposed) {
            throw IllegalStateException("$this is disposed but attempted to execute task for key $key")
        }
        var taskPair = tasks.remove(key)
        if (taskPair == null) {
            taskPair = TaskPair(UniqueAsyncTask(this, key, task))
            tasks[key] = taskPair
            taskPair.current.executeOnExecutor(executor)
            return
        }
        val (current, pending) = taskPair
        if (pending != null) {
            cancel(pending)
        }
        tasks[key] = TaskPair(current, UniqueAsyncTask(this, key, task))
        current.delegate.cancel(false)
        current.cancel(false)
    }

    fun dispose() {
        disposed = true
        val keys = tasks.keys.toList()
        for (key in keys) {
            val taskPair = tasks.remove(key)!!
            cancel(taskPair.current)
            if (taskPair.pending != null) {
                cancel(taskPair.pending)
            }
        }
    }

    private fun cancel(task: UniqueAsyncTask<Key, Result>) {
        task.delegate.cancel(false)
        task.cancel(false)
    }

    private fun onTaskFinished(finished: UniqueAsyncTask<Key, Result>) {
        if (disposed) {
            return
        }
        val key = finished.key
        var taskPair = tasks.remove(key)!!
        if (finished === taskPair.current) {
            if (finished.isCancelled) {
                listener.taskCancelled(key, finished.delegate)
            }
            else {
                listener.taskFinished(key, finished.delegate)
            }
            if (taskPair.pending != null) {
                taskPair = TaskPair(taskPair.pending!!, null)
                tasks[key] = taskPair
                taskPair.current.executeOnExecutor(executor)
            }
        }
        else if (finished.isCancelled) {
            listener.taskPreempted(finished.key, finished.delegate)
            tasks[key] = taskPair
        }
        else {
            throw IllegalStateException("finished task was not cancelled but matched pending task for key ${finished.key}")
        }
    }

    interface TaskListener<Key, Result> {

        /**
         * The task finished normally.
         */
        fun taskFinished(key: Key, task: RunnableFuture<Result>)
        /**
         * The task was cancelled after it began executing.
         */
        fun taskCancelled(key: Key, task: RunnableFuture<Result>)

        /**
         * The task was cancelled before it began executing.
         */
        fun taskPreempted(key: Key, task: RunnableFuture<Result>)
    }

    private data class TaskPair<K, Result>(val current: UniqueAsyncTask<K, Result>, val pending: UniqueAsyncTask<K, Result>? = null)

    private class UniqueAsyncTask<K, Result>(val manager: UniqueAsyncTaskManager<K, Result>, val key: K, val delegate: RunnableFuture<Result>) : AsyncTask<Void, Void, Result>() {

        override fun doInBackground(vararg params: Void?): Result? {
            if (isCancelled) {
                return null
            }
            return try {
                delegate.run()
                delegate.get()
            }
            catch (e: Exception) {
                return null
            }
        }

        override fun onPostExecute(result: Result?) {
            manager.onTaskFinished(this)
        }

        override fun onCancelled() {
            onPostExecute(null)
        }

        override fun toString(): String {
            return "${javaClass.simpleName}[$key]"
        }
    }
}