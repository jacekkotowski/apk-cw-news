package pl.cwnews;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Periodic refresh while the app is idle. Android decides the exact times. */
public class RefreshJob extends JobService {
    private static final int JOB = 1;
    private static final long MIN_MS = 15 * 60 * 1000L;

    static void schedule(Context c, int minutes) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (minutes <= 0) { js.cancel(JOB); return; }
        js.schedule(new JobInfo.Builder(JOB, new ComponentName(c, RefreshJob.class))
            .setPeriodic(Math.max(MIN_MS, minutes * 60000L))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true).build());
    }

    @Override public boolean onStartJob(JobParameters p) {
        new Thread(() -> { Refresher.all(this); jobFinished(p, false); }, "refresh-job").start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters p) { return true; }
}
