package si.jakobkreft.ontime;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.util.List;

public class TimerFragment extends Fragment {
    private static final String ARG_INDEX       = "index";
    private static final int    FLING_THRESHOLD = 200; // pixels

    // Default thresholds in ms
    private static final long DEFAULT_TOTAL  = 25 * 60 * 1000L;
    private static final long DEFAULT_YELLOW = 10 * 60 * 1000L;
    private static final long DEFAULT_RED    = 5  * 60 * 1000L;

    public interface TimerActions {
        void onTimerChanged(int index, TimerModel updated);
        void onDeleteTimer(int position);
    }

    private int          index;
    private TimerActions actions;
    private TimerModel   model;

    private View   rootView;
    private Button deleteBtn;
    private boolean pendingDelete = false;

    public static TimerFragment newInstance(int idx) {
        TimerFragment frag = new TimerFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_INDEX, idx);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onAttach(@NonNull Context ctx) {
        super.onAttach(ctx);
        if (!(ctx instanceof TimerActions)) {
            throw new RuntimeException("Host must implement TimerActions");
        }
        actions = (TimerActions) ctx;
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState) {

        rootView = inflater.inflate(R.layout.fragment_timer, container, false);
        index    = requireArguments().getInt(ARG_INDEX);
        model    = PreferencesManager
                .loadTimers(requireContext())
                .get(index);

        // Views
        ScrollView content = rootView.findViewById(R.id.content_view);
        deleteBtn          = rootView.findViewById(R.id.btn_delete);
        deleteBtn.bringToFront();

        EditText eTotal  = rootView.findViewById(R.id.et_total);
        EditText eYellow = rootView.findViewById(R.id.et_yellow);
        EditText eRed    = rootView.findViewById(R.id.et_red);

        TextView tTotal  = rootView.findViewById(R.id.tv_total);
        TextView tYellow = rootView.findViewById(R.id.tv_yellow);
        TextView tRed    = rootView.findViewById(R.id.tv_red);

        // Populate initial values
        eTotal.setText(formatMS(model.totalMillis));
        eYellow.setText(formatMS(model.yellowMillis));
        eRed.setText(formatMS(model.redMillis));

        tTotal.setText("Saved: "  + formatMS(model.totalMillis));
        tYellow.setText("Saved: " + formatMS(model.yellowMillis));
        tRed.setText("Saved: "    + formatMS(model.redMillis));

        deleteBtn.setVisibility(View.GONE);

        // TextWatcher to propagate changes
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                long t = parseMS(eTotal.getText().toString());
                long y = parseMS(eYellow.getText().toString());
                long r = parseMS(eRed.getText().toString());

                model.totalMillis  = t;
                model.yellowMillis = y;
                model.redMillis    = r;
                actions.onTimerChanged(index, model);

                tTotal.setText("Saved: "  + formatMS(t));
                tYellow.setText("Saved: " + formatMS(y));
                tRed.setText("Saved: "    + formatMS(r));
            }
        };
        eTotal.addTextChangedListener(watcher);
        eYellow.addTextChangedListener(watcher);
        eRed.addTextChangedListener(watcher);

        // GestureDetector: only allow swipe‐up if >1 timer AND not default values
        GestureDetector gd = new GestureDetector(requireContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(
                            MotionEvent e1, MotionEvent e2,
                            float vx, float vy) {

                        float dy  = e2.getY() - e1.getY();
                        float ady = Math.abs(dy);
                        float adx = Math.abs(e2.getX() - e1.getX());

                        if (ady > adx && ady > FLING_THRESHOLD) {
                            // check deletion criteria
                            List<TimerModel> all = PreferencesManager.loadTimers(requireContext());
                            boolean canDelete = all.size() > 1 && !isDefault(model);

                            if (dy < 0 && canDelete && !pendingDelete) {
                                // Swipe up → reveal delete
                                pendingDelete = true;
                                deleteBtn.setVisibility(View.VISIBLE);
                                rootView.animate()
                                        .translationY(-rootView.getHeight() / 2f)
                                        .setDuration(300)
                                        .start();
                                return true;
                            } else if (dy > 0 && pendingDelete) {
                                // Swipe down → cancel
                                pendingDelete = false;
                                rootView.animate()
                                        .translationY(0f)
                                        .setDuration(300)
                                        .withEndAction(() -> deleteBtn.setVisibility(View.GONE))
                                        .start();
                                return true;
                            }
                        }
                        return false;
                    }
                });

        content.setOnTouchListener((v, ev) -> gd.onTouchEvent(ev));

        // Delete button click → full swipe + callback
        deleteBtn.setOnClickListener(v -> {
            rootView.animate()
                    .translationY(-rootView.getHeight())
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> actions.onDeleteTimer(index))
                    .start();
        });

        return rootView;
    }

    @Override
    public void onPause() {
        super.onPause();
        // cancel pending delete if user navigates away
        if (pendingDelete) {
            pendingDelete = false;
            rootView.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(100)
                    .withEndAction(() -> deleteBtn.setVisibility(View.GONE))
                    .start();
        }
    }

    // Helper to detect default timer
    private boolean isDefault(TimerModel m) {
        return m.totalMillis  == DEFAULT_TOTAL
                && m.yellowMillis == DEFAULT_YELLOW
                && m.redMillis    == DEFAULT_RED;
    }

    private long parseMS(String s) {
        try {
            String[] p = s.split(":");
            int mm = Integer.parseInt(p[0]);
            int ss = Integer.parseInt(p[1]);
            return (mm * 60L + ss) * 1000L;
        } catch (Exception ex) {
            return 0;
        }
    }

    private String formatMS(long ms) {
        int totalSec = (int)(ms / 1000);
        int mm       = totalSec / 60;
        int ss       = totalSec % 60;
        return String.format("%02d:%02d", mm, ss);
    }
}
