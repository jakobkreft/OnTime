package si.jakobkreft.ontime;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.List;

public class TimerPagerAdapter extends FragmentStateAdapter {
    private final List<TimerModel> timers;
    private final TimerFragment.TimerActions actions;

    public TimerPagerAdapter(@NonNull FragmentActivity fa,
                             List<TimerModel> timers,
                             TimerFragment.TimerActions actions) {
        super(fa);
        this.timers  = timers;
        this.actions = actions;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return TimerFragment.newInstance(position);
    }

    @Override
    public int getItemCount() {
        return timers.size();
    }

    @Override
    public long getItemId(int position) {
        // Return the stable unique ID from the model
        return timers.get(position).id;
    }

    @Override
    public boolean containsItem(long itemId) {
        // Check if any timer still has this ID
        for (TimerModel m : timers) {
            if (m.id == itemId) return true;
        }
        return false;
    }
}
