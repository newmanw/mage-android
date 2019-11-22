package mil.nga.giat.mage.map.preference;

import android.content.Context;

import androidx.lifecycle.Lifecycle;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import mil.nga.giat.mage.R;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.Matchers.not;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class OfflineLayersPreferenceActivityTest {

    @Rule
    public ActivityScenarioRule<TileOverlayPreferenceActivity> activityRule =
            new ActivityScenarioRule(TileOverlayPreferenceActivity.class);

    @Before
    public void before(){
        activityRule.getScenario().moveToState(Lifecycle.State.RESUMED);
    }

    @Test
    public void testRefresh() throws Exception {
        onView(withId(R.id.tile_overlay_refresh)).check(matches(isDisplayed()));
        Thread.sleep(5000);
        onView(withId(R.id.tile_overlay_refresh)).check(matches(isEnabled()));

        Context context = getApplicationContext();

        onView(withId(R.id.tile_overlay_refresh)).perform(click());
        onView(withId(R.id.tile_overlay_refresh)).check(matches(isEnabled()));
        onView(withId(R.id.downloadable_layers_content)).check(matches(isDisplayed()));

    }
}
