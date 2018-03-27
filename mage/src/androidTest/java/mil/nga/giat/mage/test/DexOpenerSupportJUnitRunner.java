package mil.nga.giat.mage.test;

import android.app.Application;
import android.content.Context;
import android.support.test.runner.AndroidJUnitRunner;

import com.github.tmurakami.dexopener.DexOpener;

public class DexOpenerSupportJUnitRunner extends AndroidJUnitRunner {
    @Override
    public Application newApplication(ClassLoader cl, String className, Context context)
        throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        DexOpener.builder(context)
            .buildConfig(mil.nga.giat.mage.BuildConfig.class)
            .build()
            .installTo(cl);
        return super.newApplication(cl, className, context);
    }
}
