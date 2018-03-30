package mil.nga.giat.mage.test;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.FutureTask;

public class AsyncTesting {

    public static void waitForMainThreadToRun(Runnable task) {
        FutureTask<Void> blockUntilRun = new FutureTask<>(task, null);
        new Handler(Looper.getMainLooper()).post(blockUntilRun);
        try {
            blockUntilRun.get();
        }
        catch (Exception e) {
            throw new Error("error waiting for main thread task to complete", e);
        }
    }
}
