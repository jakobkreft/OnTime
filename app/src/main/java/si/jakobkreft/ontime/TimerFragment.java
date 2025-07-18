package si.jakobkreft.ontime;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import java.util.List;

public class TimerFragment extends Fragment {
    private static final String ARG_INDEX       = "index";
    private static final int    FLING_THRESHOLD = 15;

    // Default presets
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

    // UI refs
    private View        rootView;
    private ScrollView  contentView;
    private ImageButton      deleteBtn;
    private EditText    timeInput, yellowTimeInput, redTimeInput;
    private ImageButton playPauseButton, stopButton;
    private ProgressBar progressBar;
    private TextView    timerText, overtimeText, redDescription;

    private TextView    pageText;

    // Timer state
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private long totalTimeInMillis, yellowWarningTimeInMillis, redWarningTimeInMillis;
    private long startTimeInMillis, pausedTimeOffset, overtimeStartInMillis, pausedOvertimeOffset;
    private boolean isRunning, isPaused, pendingDelete;

    // tracks last color applied
    @ColorInt
    private int currentBgColor;

    private final Runnable updateTimerRunnable = new Runnable() {
        @Override public void run() {
            long elapsed = System.currentTimeMillis() - startTimeInMillis;
            long remaining = totalTimeInMillis - elapsed;

            if (remaining > 0) {
                updateTimerUI(remaining);
                // pick color
                @ColorInt int color;

                long redThreshold    = Math.max(0, redWarningTimeInMillis    + 1_000);
                long yellowThreshold = Math.max(0, yellowWarningTimeInMillis + 1_000);

                if (remaining <= redThreshold) {
                    color = ContextCompat.getColor(requireContext(), R.color.timer_red);
                } else if (remaining <= yellowThreshold) {
                    color = ContextCompat.getColor(requireContext(), R.color.timer_yellow);
                } else {
                    color = ContextCompat.getColor(requireContext(), R.color.timer_green);
                }

                applyBackgroundColor(color);

                timerHandler.postDelayed(this, 1000);
            } else {
                // first hit zero
                if (overtimeStartInMillis == 0) {
                    @ColorInt int red = ContextCompat.getColor(requireContext(), R.color.timer_red);
                    applyBackgroundColor(red);
                    overtimeStartInMillis = System.currentTimeMillis();
                    overtimeText.setVisibility(View.VISIBLE);
                    overtimeText.setAlpha(1f);
                }
                long ot = System.currentTimeMillis() - overtimeStartInMillis;
                updateOvertimeUI(ot);
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    public static TimerFragment newInstance(int idx) {
        TimerFragment f = new TimerFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_INDEX, idx);
        f.setArguments(args);
        return f;
    }

    @Override public void onAttach(@NonNull Context ctx) {
        super.onAttach(ctx);
        if (!(ctx instanceof TimerActions)) {
            throw new RuntimeException("Host must implement TimerActions");
        }
        actions = (TimerActions) ctx;
    }


    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        // save running/paused state and timing offsets
        out.putBoolean("isRunning",       isRunning);
        out.putBoolean("isPaused",        isPaused);
        out.putLong   ("startTime",       startTimeInMillis);
        out.putLong   ("pausedOffset",    pausedTimeOffset);
        out.putLong   ("overtimeStart",   overtimeStartInMillis);
        out.putLong   ("pausedOvertime",  pausedOvertimeOffset);
        out.putInt    ("bgColor",         currentBgColor);
    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inf,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        // 1) Inflate & bind
        rootView = inf.inflate(R.layout.fragment_timer, container, false);
        contentView       = rootView.findViewById(R.id.content_view);
        deleteBtn         = rootView.findViewById(R.id.btn_delete);
        timeInput         = rootView.findViewById(R.id.timeInput);
        yellowTimeInput   = rootView.findViewById(R.id.yellowTime);
        redTimeInput      = rootView.findViewById(R.id.redTime);
        playPauseButton   = rootView.findViewById(R.id.playPauseButton);
        stopButton        = rootView.findViewById(R.id.stopButton);
        progressBar       = rootView.findViewById(R.id.progressBar);
        timerText         = rootView.findViewById(R.id.timerText);
        overtimeText      = rootView.findViewById(R.id.overtimeText);
        redDescription    = rootView.findViewById(R.id.redDescription);
        pageText         = rootView.findViewById(R.id.PageText);

        ImageButton aboutButton = rootView.findViewById(R.id.AboutButton);

        // 2) “About” tap
        aboutButton.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AboutActivity.class))
        );

        // 3) Load the model & show static inputs
        index = requireArguments().getInt(ARG_INDEX);
        model = PreferencesManager.loadTimers(requireContext()).get(index);
        timeInput.setText(formatTime(model.totalMillis));
        yellowTimeInput.setText(formatTime(model.yellowMillis));
        redTimeInput.setText(formatTime(model.redMillis));

        // 4) Sync local threshold vars
        totalTimeInMillis         = model.totalMillis;
        yellowWarningTimeInMillis = model.yellowMillis;
        redWarningTimeInMillis    = model.redMillis;

        // 5) Hide delete-button until swipe
        deleteBtn.setVisibility(View.GONE);
// 6) Restore live state from model
        if (model.isRunning) {
            isRunning             = true;
            isPaused              = model.isPaused;

            // ← map model → local
            startTimeInMillis     = model.startAt;
            pausedTimeOffset      = model.pausedOffset;
            overtimeStartInMillis = model.overtimeAt;
            pausedOvertimeOffset  = model.otPausedOffset;

            // allow stopping
            stopButton.setEnabled(true);

            if (isPaused) {
                // paused → show remaining & play icon
                playPauseButton.setImageResource(R.drawable.ic_play);
                long remaining = Math.max(0, totalTimeInMillis - pausedTimeOffset);
                updateTimerUI(remaining);
            } else {
                // actively running → pause icon + resume updates
                playPauseButton.setImageResource(R.drawable.ic_pause);
                timerHandler.post(updateTimerRunnable);
            }

            // if already in overtime
            if (overtimeStartInMillis > 0) {
                overtimeText.setVisibility(View.VISIBLE);
                updateOvertimeUI(System.currentTimeMillis() - overtimeStartInMillis);
            }

        } else {
            // not running → full reset
            resetTimerState();
        }

        // 7) Wire up controls & gestures
        setupInputListeners();
        playPauseButton.setOnClickListener(v -> handlePlayPause());
        stopButton.setOnClickListener(v -> handleStop());
        setupDeleteGesture();

        updatePageIndicator();

// —— add the tap-hint listener here ——
        pageText.setOnClickListener(v -> {
            ViewPager2 pager = requireActivity().findViewById(R.id.viewPager);
            int total    = pager.getAdapter().getItemCount();
            int position = pager.getCurrentItem() + 1;  // 1-based index

            String msg;
            if (position == 1 && total == 1) {
                msg = "Edit this timer to unlock additional timers.";
            } else if (position == 1) {
                msg = "Swipe right to access the next timer.";
            } else if (position == total) {
                msg = "Edit this timer to unlock additional timers.";
            } else {
                msg = "Swipe up on this timer to delete it.";
            }

            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        });


        return rootView;
    }


    @Override public void onResume() {
        super.onResume();
        // re-sync container color when this fragment becomes visible
        requireActivity()
                .findViewById(R.id.main_container)
                .setBackgroundColor(currentBgColor);
        applyBackgroundColor(currentBgColor);

        updatePageIndicator();
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        timerHandler.removeCallbacks(updateTimerRunnable);
    }

    // ─── Helpers ───────────────────────────────────────────────

    /**
     * Call this whenever you want to change the timer’s color.
     * It paints the fragment root, the activity container,
     * plus the system bars (status + navigation).
     */
    private void applyBackgroundColor(@ColorInt int color) {
        currentBgColor = color;

        // 1) Fragment’s own background always updates
        rootView.setBackgroundColor(color);

        // 2) Only the _visible_ page should paint the activity container & system bars
        ViewPager2 pager = requireActivity().findViewById(R.id.viewPager);
        if (pager.getCurrentItem() == index) {
            // activity container behind the tabs
            requireActivity()
                    .findViewById(R.id.main_container)
                    .setBackgroundColor(color);

        }
    }

    private void setupInputListeners() {
        TextWatcher saver = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                // keep model & prefs in sync
                model.totalMillis  = parseTimeInput(timeInput.getText().toString());
                model.yellowMillis = parseTimeInput(yellowTimeInput.getText().toString());
                model.redMillis    = parseTimeInput(redTimeInput.getText().toString());
                actions.onTimerChanged(index, model);
            }
        };

        // apply to all three
        timeInput.addTextChangedListener(saver);
        yellowTimeInput.addTextChangedListener(saver);
        redTimeInput.addTextChangedListener(saver);

        View.OnFocusChangeListener focusListener = (v, hasFocus) -> {
            if (!hasFocus) {
                // user just left an input → re‐parse & update UI immediately
                totalTimeInMillis        = parseTimeInput(timeInput.getText().toString());
                yellowWarningTimeInMillis = parseTimeInput(yellowTimeInput.getText().toString());
                redWarningTimeInMillis    = parseTimeInput(redTimeInput.getText().toString());

                // refresh the large timer text + progress bar + background color
                updateTimerUI(totalTimeInMillis);

                // you may also want to re‐apply the last color if thresholds moved:
                // long colorThreshold = remaining logic...
                // applyBackgroundColor( currentBgColor );
            }
        };

        timeInput.setOnFocusChangeListener(focusListener);
        yellowTimeInput.setOnFocusChangeListener(focusListener);
        redTimeInput.setOnFocusChangeListener(focusListener);
    }

    private void updatePageIndicator() {
        ViewPager2 pager = requireActivity().findViewById(R.id.viewPager);
        int total = pager.getAdapter().getItemCount();
        // index is zero-based, but we want “1/…”
        pageText.setText((index + 1) + "/" + total);
    }


    private void resetTimerState() {
        // reset flags & offsets
        isRunning = false; isPaused = false; pausedTimeOffset = 0;
        overtimeStartInMillis = 0; pausedOvertimeOffset = 0;

        totalTimeInMillis        = model.totalMillis;
        yellowWarningTimeInMillis = model.yellowMillis;
        redWarningTimeInMillis    = model.redMillis;

        updateTimerUI(totalTimeInMillis);
        overtimeText.setVisibility(View.GONE);

        // default green
        @ColorInt int green = ContextCompat.getColor(requireContext(), R.color.timer_green);
        applyBackgroundColor(green);
        progressBar.setProgress(0);
        playPauseButton.setImageResource(R.drawable.ic_play);
    }
    // replace your existing setupDeleteGesture() with this entire method:
    private void setupDeleteGesture() {
        // 1) Create a GestureDetector to pick up long‐presses
        GestureDetector gestureDetector = new GestureDetector(requireContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public void onLongPress(MotionEvent e) {
                        Toast.makeText(requireContext(),
                                "Swipe up to delete. Swipe right for new timer.",
                                Toast.LENGTH_SHORT).show();
                    }
                });

        // 2) Your existing swipe logic…
        View.OnTouchListener swipeListener = new View.OnTouchListener() {
            float startY;
            @Override
            public boolean onTouch(View v, MotionEvent ev) {
                // First, give the GestureDetector a crack at it
                gestureDetector.onTouchEvent(ev);

                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = ev.getY();
                        if (pendingDelete) {
                            pendingDelete = false;
                            rootView.animate()
                                    .translationY(0f)
                                    .setDuration(200)
                                    .withEndAction(() -> deleteBtn.setVisibility(View.GONE))
                                    .start();
                            return false;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        float dy = ev.getY() - startY;
                        List<TimerModel> all = PreferencesManager.loadTimers(requireContext());
                        boolean canDelete = all.size() > 1 && !isDefault(model);

                        if (dy < -FLING_THRESHOLD) {
                            if (canDelete && !pendingDelete) {
                                pendingDelete = true;
                                deleteBtn.setVisibility(View.VISIBLE);
                                rootView.animate()
                                        .translationY(-rootView.getHeight() / 3f)
                                        .setDuration(300)
                                        .start();
                            } else if (!canDelete) {
                                Toast.makeText(requireContext(),
                                        "Cannot delete this timer.",
                                        Toast.LENGTH_SHORT).show();
                            }
                            return true;
                        } else if (dy > FLING_THRESHOLD && pendingDelete) {
                            pendingDelete = false;
                            rootView.animate()
                                    .translationY(0f)
                                    .setDuration(300)
                                    .withEndAction(() -> deleteBtn.setVisibility(View.GONE))
                                    .start();
                            return true;
                        }
                        break;
                }
                return false;
            }
        };

        // 3) Attach listener to your scrollable areas
        View[] swipeTargets = new View[] {
                rootView.findViewById(R.id.content_view),
                rootView.findViewById(R.id.right_content_view)
        };
        for (View target : swipeTargets) {
            if (target != null) {
                target.setOnTouchListener(swipeListener);
            }
        }

        // 4) Existing delete button handler remains unchanged
        deleteBtn.setOnClickListener(v -> {
            stopTimer();       // clear running state
            resetTimerState(); // reset UI

            rootView.animate()
                    .translationY(-rootView.getHeight())
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> {
                        timerHandler.removeCallbacks(updateTimerRunnable);
                        actions.onDeleteTimer(index);
                    })
                    .start();
        });
    }


    // Timer controls (start, pause, resume, stop, validation)
    private void handlePlayPause() {
        if (!isRunning) {
            totalTimeInMillis        = parseTimeInput(timeInput.getText().toString());
            yellowWarningTimeInMillis = parseTimeInput(yellowTimeInput.getText().toString());
            redWarningTimeInMillis    = parseTimeInput(redTimeInput.getText().toString());
            if (!validateInputs()) return;
            startTimer();
        } else if (isPaused) {
            resumeTimer();
        } else {
            pauseTimer();
        }
    }

    private void handleStop() {
        stopTimer();
        resetTimerState();
        stopButton.setEnabled(false);
    }
    private void startTimer() {
        startTimeInMillis = System.currentTimeMillis() - pausedTimeOffset;
        isRunning = true;
        isPaused  = false;

        // sync back to model
        model.isRunning       = true;
        model.isPaused        = false;
        model.startAt         = startTimeInMillis;
        model.pausedOffset    = pausedTimeOffset;
        model.overtimeAt      = overtimeStartInMillis;
        model.otPausedOffset  = pausedOvertimeOffset;
        actions.onTimerChanged(index, model);

        playPauseButton.setImageResource(R.drawable.ic_pause);
        stopButton.setEnabled(true);
        timerHandler.post(updateTimerRunnable);
    }

    private void pauseTimer() {
        isPaused = true;
        pausedTimeOffset = System.currentTimeMillis() - startTimeInMillis;
        if (overtimeStartInMillis > 0) {
            pausedOvertimeOffset = System.currentTimeMillis() - overtimeStartInMillis;
        }

        // sync back to model
        model.isRunning       = true;
        model.isPaused        = true;
        model.startAt         = startTimeInMillis;
        model.pausedOffset    = pausedTimeOffset;
        model.overtimeAt      = overtimeStartInMillis;
        model.otPausedOffset  = pausedOvertimeOffset;
        actions.onTimerChanged(index, model);

        playPauseButton.setImageResource(R.drawable.ic_play);
        timerHandler.removeCallbacks(updateTimerRunnable);
    }

    private void resumeTimer() {
        if (overtimeStartInMillis > 0) {
            overtimeStartInMillis = System.currentTimeMillis() - pausedOvertimeOffset;
            pausedOvertimeOffset  = 0;
        }
        startTimer();  // will sync back again
    }

    private void stopTimer() {
        isRunning            = false;
        isPaused             = false;
        startTimeInMillis    = 0;
        pausedTimeOffset     = 0;
        overtimeStartInMillis= 0;
        pausedOvertimeOffset = 0;

        // sync back to model
        model.isRunning       = false;
        model.isPaused        = false;
        model.startAt         = 0;
        model.pausedOffset    = 0;
        model.overtimeAt      = 0;
        model.otPausedOffset  = 0;
        actions.onTimerChanged(index, model);

        playPauseButton.setImageResource(R.drawable.ic_play);
        timerHandler.removeCallbacks(updateTimerRunnable);

        @ColorInt int green = ContextCompat.getColor(requireContext(), R.color.timer_green);
        applyBackgroundColor(green);
        progressBar.setProgress(0);
        overtimeText.setVisibility(View.GONE);
    }


    private boolean validateInputs() {
        if (totalTimeInMillis <= 0) {
            showToast("Total time must be greater than zero.");
            return false;
        }
        if (redWarningTimeInMillis <= 0) {
            redWarningTimeInMillis = 1;
        }
        if (redWarningTimeInMillis >= yellowWarningTimeInMillis) {
            showToast("Red warning must be < yellow warning.");
            return false;
        }
        if (yellowWarningTimeInMillis >= totalTimeInMillis ||
                redWarningTimeInMillis >= totalTimeInMillis) {
            showToast("Warning times must be < total time.");
            return false;
        }
        return true;
    }

    private void updateTimerUI(long ms) {
        if (totalTimeInMillis <= 0) {
            timerText.setText("0");
            progressBar.setProgress(0);
            return;
        }
        if (ms <= 0) ms = totalTimeInMillis;
        timerText.setText(formatTime(ms));
        int prog = (int)((totalTimeInMillis - ms + 1000) * 100 / totalTimeInMillis);
        progressBar.setProgress(prog);
    }

    private void updateOvertimeUI(long ms) {
        overtimeText.setText("+" + formatTime(ms));
    }

    private long parseTimeInput(String s) {
        try {
            if (s==null || s.isEmpty()) return 0;
            String normalized = s.trim().replaceAll("[\\.,\\s]+", ":");  // dots, commas or spaces → colon
            String[] p = normalized.split(":");
            int h=0,m=0,sec=0;
            if (p.length==3) { h=Integer.parseInt(p[0]); m=Integer.parseInt(p[1]); sec=Integer.parseInt(p[2]); }
            else if (p.length==2){ m=Integer.parseInt(p[0]); sec=Integer.parseInt(p[1]); }
            else if (p.length==1){ sec=Integer.parseInt(p[0]); }
            return (h*3600L + m*60L + sec)*1000L;
        } catch(Exception ex) { return 0; }
    }

    private String formatTime(long ms) {
        long totalSec = ms/1000;
        int h = (int)(totalSec/3600);
        int m = (int)((totalSec%3600)/60);
        int s = (int)(totalSec%60);
        if (h>0) return String.format("%d:%02d:%02d",h,m,s);
        if (m>0) return String.format("%d:%02d",m,s);
        return String.format("%d",s);
    }

    private void showToast(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private boolean isDefault(TimerModel m) {
        return m.totalMillis==DEFAULT_TOTAL
                && m.yellowMillis==DEFAULT_YELLOW
                && m.redMillis   ==DEFAULT_RED;
    }
}
