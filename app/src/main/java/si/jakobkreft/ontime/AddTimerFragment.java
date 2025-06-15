package si.jakobkreft.ontime;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

public class AddTimerFragment extends Fragment {
    interface AddTimerListener {
        void onAddTimerRequested();
    }
    private AddTimerListener listener;
    private boolean added = false;

    public static AddTimerFragment newInstance() {
        return new AddTimerFragment();
    }

    @Override
    public void onAttach(@NonNull Context ctx) {
        super.onAttach(ctx);
        if (!(ctx instanceof AddTimerListener)) {
            throw new RuntimeException("Host must implement AddTimerListener");
        }
        listener = (AddTimerListener) ctx;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inf, ViewGroup p, Bundle s) {
        return inf.inflate(R.layout.fragment_add_timer, p, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        // Post to the queue so it runs after ViewPager2’s internal transactions complete
        view.post(() -> {
            if (!added) {
                added = true;
                listener.onAddTimerRequested();
            }
        });
    }
}
