package android.os;
import java.util.ArrayList;
import java.util.Comparator;
public final class Handler {
    private record Task(Runnable run, long time) {}
    private static final ArrayList<Task> queue = new ArrayList<>();
    public Handler(Looper looper) {}
    public void postDelayed(Runnable run, long delay) { queue.add(new Task(run, SystemClock.now + delay)); }
    public void removeCallbacks(Runnable run) { queue.removeIf(task -> task.run == run); }
    public static void advance(long milliseconds) {
        long end = SystemClock.now + milliseconds;
        while (true) {
            queue.sort(Comparator.comparingLong(Task::time));
            if (queue.isEmpty() || queue.get(0).time > end) break;
            Task task = queue.remove(0);
            SystemClock.now = task.time;
            task.run.run();
        }
        SystemClock.now = end;
    }
}
