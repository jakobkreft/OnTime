package si.jakobkreft.ontime;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;


public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "PresentationTimerPrefs";
    private static final String TOTAL_TIME_KEY = "totalTime";
    private static final String YELLOW_TIME_KEY = "yellowTime";
    private static final String RED_TIME_KEY = "redTime";

    private EditText timeInput, yellowTimeInput, redTimeInput;
    private TextView timerText, overtimeText;
    private ImageButton playPauseButton, stopButton;

    private Handler timerHandler = new Handler();
    private long totalTimeInMillis, yellowWarningTimeInMillis, redWarningTimeInMillis;
    private long startTimeInMillis;
    private boolean isRunning = false;
    private boolean isPaused = false;
    private long pausedTimeOffset = 0;
    private long overtimeStartInMillis = 0;
    private long pausedOvertimeOffset = 0; // New variable to track paused overtime duration
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Handle system window insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ImageButton aboutButton = findViewById(R.id.AboutButton);
        aboutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, AboutActivity.class);
                startActivity(intent);
            }
        });
        // Initialize UI elements
        timeInput = findViewById(R.id.timeInput);
        yellowTimeInput = findViewById(R.id.yellowTime);
        redTimeInput = findViewById(R.id.redTime);
        timerText = findViewById(R.id.timerText);
        overtimeText = findViewById(R.id.overtimeText);
        playPauseButton = findViewById(R.id.playPauseButton);
        stopButton = findViewById(R.id.stopButton);
        progressBar = findViewById(R.id.progressBar);

        // Load user input preferences
        loadPreferences();

        // Reset timer state on app start (fresh start)
        resetTimerState();

        // Set initial button states
        stopButton.setEnabled(false);

        // Set click listeners
        playPauseButton.setOnClickListener(v -> handlePlayPause());
        stopButton.setOnClickListener(v -> handleStop());

        // Add TextWatchers for immediate preference update
        setupTextWatchers();

    }

    private void setupTextWatchers() {
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                savePreferences();
                updateTimerUI(totalTimeInMillis);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        timeInput.addTextChangedListener(textWatcher);
        yellowTimeInput.addTextChangedListener(textWatcher);
        redTimeInput.addTextChangedListener(textWatcher);
    }

    private void resetTimerState() {
        startTimeInMillis = 0;
        isRunning = false;
        isPaused = false;
        pausedTimeOffset = 0;
        overtimeStartInMillis = 0;

        // Update the total time to the user input
        totalTimeInMillis = parseTimeInput(timeInput.getText().toString());
        updateTimerUI(totalTimeInMillis);

        // Hide the overtime text
        overtimeText.setVisibility(View.GONE);

        // Reset background and progress bar
        findViewById(R.id.main).setBackgroundColor(getResources().getColor(R.color.timer_green));
        progressBar.setProgress(0); // Reset progress bar

        playPauseButton.setImageResource(R.drawable.ic_play);
    }


    private void handlePlayPause() {
        if (!isRunning) {
            if (!parseAndValidateInput()) return;
            startTimer();
        } else {
            if (isPaused) {
                resumeTimer();
            } else {
                pauseTimer();
            }
        }
    }

    private void handleStop() {
        stopTimer();
        resetTimerState();
    }


    private void startTimer() {
        startTimeInMillis = System.currentTimeMillis() - pausedTimeOffset;
        isRunning = true;
        isPaused = false;
        pausedTimeOffset = 0;
        savePreferences();

        playPauseButton.setImageResource(R.drawable.ic_pause);
        stopButton.setEnabled(true);
        timerHandler.post(updateTimerRunnable);
    }

    private void pauseTimer() {
        isPaused = true;
        pausedTimeOffset = System.currentTimeMillis() - startTimeInMillis;

        // If we are in overtime, calculate and store the paused overtime offset
        if (overtimeStartInMillis > 0) {
            pausedOvertimeOffset = System.currentTimeMillis() - overtimeStartInMillis;
        }

        playPauseButton.setImageResource(R.drawable.ic_play);
        timerHandler.removeCallbacks(updateTimerRunnable);
        savePreferences();
    }

    private void resumeTimer() {
        if (overtimeStartInMillis > 0) {
            // Adjust the overtime start time using the paused overtime offset
            overtimeStartInMillis = System.currentTimeMillis() - pausedOvertimeOffset;
            pausedOvertimeOffset = 0; // Reset the paused offset
        }

        startTimer(); // Restart using the system time and offset
    }


    private void stopTimer() {
        isRunning = false;
        isPaused = false;
        startTimeInMillis = 0;
        pausedTimeOffset = 0;
        overtimeStartInMillis = 0;

        // Reset UI elements
        playPauseButton.setImageResource(R.drawable.ic_play);
        stopButton.setEnabled(false);
        timerHandler.removeCallbacks(updateTimerRunnable);

        // Reset background and progress bar
        findViewById(R.id.main).setBackgroundColor(getResources().getColor(R.color.timer_green));
        progressBar.setProgress(0); // Reset progress bar

        // Hide the overtime text
        overtimeText.setVisibility(View.GONE);

        updateTimerUI(0);

        savePreferences();
    }

    private final Runnable updateTimerRunnable = new Runnable() {
        @Override
        public void run() {
            long elapsedMillis = System.currentTimeMillis() - startTimeInMillis;
            long remainingTime = totalTimeInMillis - elapsedMillis;

            if (remainingTime > 0) {
                updateTimerUI(remainingTime);

                // Handle color changes based on remaining time
                if (remainingTime <= redWarningTimeInMillis) {
                    findViewById(R.id.main).setBackgroundColor(getResources().getColor(R.color.timer_red));
                } else if (remainingTime <= yellowWarningTimeInMillis) {
                    findViewById(R.id.main).setBackgroundColor(getResources().getColor(R.color.timer_yellow));
                } else {
                    findViewById(R.id.main).setBackgroundColor(getResources().getColor(R.color.timer_green));
                }

                timerHandler.postDelayed(this, 1000);
            } else {
                // When the timer hits 0, explicitly change the background to red
                if (overtimeStartInMillis == 0) {
                    findViewById(R.id.main).setBackgroundColor(getResources().getColor(R.color.timer_red)); // Ensure red color at time 0
                    overtimeStartInMillis = System.currentTimeMillis();

                    // Show the overtime text
                    overtimeText.setVisibility(View.VISIBLE);
                    overtimeText.setAlpha(1f); // Ensure the text is fully visible

                }

                // Update overtime
                long overtimeElapsed = System.currentTimeMillis() - overtimeStartInMillis;
                updateOvertimeUI(overtimeElapsed);

                timerHandler.postDelayed(this, 1000);
            }
        }
    };


    private void updateOvertimeUI(long millis) {
        overtimeText.setText("+" + formatTime(millis));
    }

    private String formatTime(long millis) {
        int hours = (int) (millis / 1000) / 3600;
        int minutes = (int) ((millis / 1000) % 3600) / 60;
        int seconds = (int) (millis / 1000) % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%d:%02d", minutes, seconds);
        } else {
            return String.format("%d", seconds);
        }
    }

    private boolean parseAndValidateInput() {
        totalTimeInMillis = parseTimeInput(timeInput.getText().toString());
        yellowWarningTimeInMillis = parseTimeInput(yellowTimeInput.getText().toString());
        redWarningTimeInMillis = parseTimeInput(redTimeInput.getText().toString());

        if (totalTimeInMillis <= 0) {
            showToast("Total time must be greater than zero.");
            return false;
        }

        // If red warning time is 0 or empty, set it to trigger right before the timer ends
        if (redWarningTimeInMillis == 0) {
            redWarningTimeInMillis = 1; // Ensure it's set to the last millisecond
        }

        if (redWarningTimeInMillis >= yellowWarningTimeInMillis) {
            showToast("Red warning time must be less than orange warning time.");
            return false;
        }

        if (yellowWarningTimeInMillis >= totalTimeInMillis || redWarningTimeInMillis >= totalTimeInMillis) {
            showToast("Warning times must be less than the total time.");
            return false;
        }

        return true;
    }

    private void updateTimerUI(long millis) {
        if (millis <= 0) {
            millis = totalTimeInMillis;
        }

        // Update the timer text
        timerText.setText(formatTime(millis));

        // Update the progress bar
        int progress = (int) ((totalTimeInMillis - millis + 1000) * 100 / totalTimeInMillis); // Calculate progress percentage
        progressBar.setProgress(progress); // Set progress in the ProgressBar
    }

    private long parseTimeInput(String timeStr) {
        try {
            if (timeStr == null || timeStr.isEmpty()) {
                return 0; // Return 0 for empty input
            }
            int hours = 0, minutes = 0, seconds = 0;
            String[] parts = timeStr.split(":");
            if (parts.length == 3) {
                hours = Integer.parseInt(parts[0]);
                minutes = Integer.parseInt(parts[1]);
                seconds = Integer.parseInt(parts[2]);
            } else if (parts.length == 2) {
                minutes = Integer.parseInt(parts[0]);
                seconds = Integer.parseInt(parts[1]);
            } else if (parts.length == 1) {
                seconds = Integer.parseInt(parts[0]);
            }
            return (hours * 3600 + minutes * 60 + seconds) * 1000;
        } catch (NumberFormatException e) {
            return 0; // Default to 0 if input is invalid
        }
    }


    private void savePreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(TOTAL_TIME_KEY, timeInput.getText().toString());
        editor.putString(YELLOW_TIME_KEY, yellowTimeInput.getText().toString());
        editor.putString(RED_TIME_KEY, redTimeInput.getText().toString());
        editor.apply();
    }

    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        timeInput.setText(prefs.getString(TOTAL_TIME_KEY, "25:00"));
        yellowTimeInput.setText(prefs.getString(YELLOW_TIME_KEY, "10:00"));
        redTimeInput.setText(prefs.getString(RED_TIME_KEY, "5:00"));
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            View view = getCurrentFocus();
            if (view instanceof EditText) {
                int[] location = new int[2];
                view.getLocationOnScreen(location);
                float x = ev.getRawX() + view.getLeft() - location[0];
                float y = ev.getRawY() + view.getTop() - location[1];

                // Check if the touch event is outside the bounds of the focused EditText
                if (x < view.getLeft() || x > view.getRight() || y < view.getTop() || y > view.getBottom()) {
                    hideKeyboard();  // Hide the keyboard
                    view.clearFocus();  // Clear the focus from the EditText
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

}
