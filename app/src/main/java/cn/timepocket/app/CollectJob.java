package cn.timepocket.app;

import android.app.job.*;
import java.util.concurrent.Future;

public final class CollectJob extends JobService {
    private Future<?> work;
    @Override public boolean onStartJob(JobParameters params) {
        work=PocketApp.IO.submit(()-> {
            boolean retry=false;
            try {Collector.sync(this);} catch(Exception e) {retry=true;}
            jobFinished(params,retry);
        });return true;
    }
    @Override public boolean onStopJob(JobParameters params) {if(work!=null)work.cancel(true);return true;}
}
