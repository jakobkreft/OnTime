package si.jakobkreft.ontime;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
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

    private TabLayout         tabLayout;
    private TabLayoutMediator tabMediator;
    // in MainActivity.java
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Force the currently-visible fragment to tear down & re-inflate its view
        int current = viewPager.getCurrentItem();
        adapter.notifyItemChanged(current);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EdgeToEdge.enable(this);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

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

        viewPager = findViewById(R.id.viewPager);
        adapter   = new TimerPagerAdapter(this, timers, this);
        viewPager.setAdapter(adapter);

        tabLayout   = findViewById(R.id.tab_layout);
        tabMediator = new TabLayoutMediator(tabLayout, viewPager, (tab, pos) -> {
            tab.setText("");
        });
        tabMediator.attach();
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

    @Override
    public void onDeleteTimer(int position) {
        if (timers.size() <= 1) return;

        // 1) Remove the model + persist
        timers.remove(position);
        PreferencesManager.saveTimers(this, timers);

        // 2) Decide new current page
        int newPos = Math.min(position, timers.size() - 1);

        // 3) Swap in a fresh adapter
        adapter = new TimerPagerAdapter(this, timers, this);
        viewPager.setAdapter(adapter);

        // 4) Tear down the old mediator…
        tabMediator.detach();

        // 5) …and create & attach a new one to sync the dots
        tabMediator = new TabLayoutMediator(tabLayout, viewPager, (tab, pos) -> {
            tab.setText("");
        });
        tabMediator.attach();

        // 6) Move to the correct page
        viewPager.setCurrentItem(newPos, true);
    }


    /** True if a model matches exactly your default 25:00 / 10:00 / 5:00. */
    private boolean isDefault(TimerModel m) {
        return m.totalMillis  == DEFAULT_TOTAL
                && m.yellowMillis == DEFAULT_YELLOW
                && m.redMillis    == DEFAULT_RED;
    }


    /** Hide keyboard and drop focus when tapping outside any EditText */
    private void hideKeyboardAndClearFocus() {
        View focused = getCurrentFocus();
        if (focused instanceof EditText) {
            InputMethodManager imm =
                    (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
            focused.clearFocus();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            View focused = getCurrentFocus();
            if (focused instanceof EditText) {
                // figure out if the touch was outside the focused EditText
                Rect outRect = new Rect();
                focused.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int)ev.getRawX(), (int)ev.getRawY())) {
                    hideKeyboardAndClearFocus();
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }
}


