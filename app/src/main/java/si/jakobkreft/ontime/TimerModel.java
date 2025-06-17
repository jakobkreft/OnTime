// TimerModel.java
package si.jakobkreft.ontime;

public class TimerModel {
    public final long id;               // stable unique identifier

    // static configuration
    public long totalMillis;
    public long yellowMillis;
    public long redMillis;

    // ——— live run‐state ———
    public boolean isRunning  = false;
    public boolean isPaused   = false;
    public long startAt       = 0L;   // System.currentTimeMillis() at start (or resume)
    public long pausedOffset  = 0L;   // ms already run before pausing
    public long overtimeAt    = 0L;   // when we crossed zero
    public long otPausedOffset= 0L;   // ms overtime before pausing

    public TimerModel() {
        this.id            = System.currentTimeMillis() + (int)(Math.random() * 1000);
        this.totalMillis   = 25 * 60 * 1000;
        this.yellowMillis  = 10 * 60 * 1000;
        this.redMillis     = 5  * 60 * 1000;
    }
}
