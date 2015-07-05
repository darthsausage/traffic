/**
 * 
 */
package traffic;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Task to run every n-minutes to collect and log traffic data
 * @author alan
 */
public class TrafficLoggerTask {
	private ScheduledExecutorService scheduler =  Executors.newScheduledThreadPool(1);
	
	public void logEvery(final long delay, final TimeUnit durationUnit, final Checker trafficChecker) {
		final Runnable logger = new Runnable() {
			public void run() {
				trafficChecker.force();
			}
		};
		
		scheduler.scheduleAtFixedRate(logger, 0, delay, durationUnit);
	}
	
	public static void createAndSchedule(final long delay, final TimeUnit durationUnit, final Checker trafficChecker) {
		TrafficLoggerTask task = new TrafficLoggerTask();
		task.logEvery(delay, durationUnit, trafficChecker);
	}
}