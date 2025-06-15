package si.jakobkreft.ontime;

public class TimerModel {
    public final long id;       // stable unique identifier
    public long totalMillis;
    public long yellowMillis;
    public long redMillis;

    public TimerModel() {
        this.id            = System.currentTimeMillis() + (int)(Math.random() * 1000);
        this.totalMillis   = 25 * 60 * 1000;
        this.yellowMillis  = 10 * 60 * 1000;
        this.redMillis     = 5  * 60 * 1000;
    }
}
