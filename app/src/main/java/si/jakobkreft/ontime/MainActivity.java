package si.jakobkreft.ontime;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.List;
public class MainActivity extends AppCompatActivity
        implements TimerFragment.TimerActions {

    // Default thresholds in milliseconds
    private static final long DEFAULT_TOTAL  = 25 * 60 * 1000L;
    private static final long DEFAULT_YELLOW = 10 * 60 * 1000L;
    private static final long DEFAULT_RED    = 5  * 60 * 1000L;

    private ViewPager2        viewPager;
    private TimerPagerAdapter adapter;
    private List<TimerModel>  timers;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // load & maybe seed your timers...
        timers = PreferencesManager.loadTimers(this);
        if (timers.isEmpty()) {
            timers.add(new TimerModel());
        }
        TimerModel last = timers.get(timers.size() - 1);
        if (!isDefault(last)) {
            timers.add(new TimerModel());
        }
        PreferencesManager.saveTimers(this, timers);

        // 1) ViewPager2 + adapter
        viewPager = findViewById(R.id.viewPager);
        adapter   = new TimerPagerAdapter(this, timers, this);
        viewPager.setAdapter(adapter);

        // 2) Dot indicator via TabLayout
        TabLayout tabs = findViewById(R.id.tab_layout);
        new TabLayoutMediator(tabs, viewPager, (tab, pos) -> {
            // no text—dots only
            tab.setText("");
        }).attach();
    }



    /** Called by a TimerFragment when its fields change. */
    @Override
    public void onTimerChanged(int index, TimerModel updated) {
        timers.set(index, updated);
        PreferencesManager.saveTimers(this, timers);

        // If it was the last page AND user turned it from default → non-default, append blank
        if (index == timers.size() - 1 && !isDefault(updated)) {
            viewPager.post(() -> {
                timers.add(new TimerModel());
                PreferencesManager.saveTimers(MainActivity.this, timers);
                adapter.notifyItemInserted(timers.size() - 1);
            });
        }
    }

    /** Called by a TimerFragment when the user swipes up to delete it. */
    @Override
    public void onDeleteTimer(int position) {
        // Never delete the last remaining timer
        if (timers.size() <= 1) return;

        viewPager.post(() -> {
            timers.remove(position);
            PreferencesManager.saveTimers(MainActivity.this, timers);

            adapter.notifyItemRemoved(position);
            adapter.notifyItemRangeChanged(position, timers.size());

            // Snap to a valid page: if you deleted the last one, go to new last; else stay at same index
            int newPos = Math.min(position, timers.size() - 1);
            viewPager.setCurrentItem(newPos, true);
        });
    }

    /** True if a model matches exactly your default 25:00 / 10:00 / 5:00. */
    private boolean isDefault(TimerModel m) {
        return m.totalMillis  == DEFAULT_TOTAL
                && m.yellowMillis == DEFAULT_YELLOW
                && m.redMillis    == DEFAULT_RED;
    }
}
