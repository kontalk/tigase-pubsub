package tigase.pubsub.modules.commands;

import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractLoadRunner implements Runnable {

	protected final Logger log = Logger.getLogger(this.getClass().getName());
	private final long delay;

	/**
	 * Test time in seconds.
	 */
	private final long testTime;
	private int counter = 0;
	private long testStartTime;
	private long testEndTime;

	public AbstractLoadRunner(long time, long frequency) {
		this.delay = (long) ((1.0 / frequency) * 1000.0);
		this.testTime = time;
		log.info("Preparing load test: testTime=" + testTime + ", frequency=" + frequency + "/sec; calculatedDelay=" + delay
				+ " ms");
	}

	protected abstract void doWork() throws Exception;

	public int getCounter() {
		return counter;
	}

	public long getDelay() {
		return delay;
	}

	public long getTestEndTime() {
		return testEndTime;
	}

	public long getTestStartTime() {
		return testStartTime;
	}

	protected void onTestFinish(){}

	@Override
	public void run() {
		try {
			this.testStartTime = System.currentTimeMillis();
			this.testEndTime = testStartTime + testTime * 1000;

			long cst;
			while (testEndTime >= (cst = System.currentTimeMillis())) {
				++counter;

				doWork();

				// do not add code under this line ;-)
				final long now = System.currentTimeMillis();
				final long dt = now - cst;
				final long fix = (testStartTime + delay * (counter - 1)) - now;
				final long sleepTime = delay - dt + fix;
				// System.out.println(new Date() + " :: " + delay + ", " + dt +
				// ", " + fix + ", " + sleepTime);
				if (sleepTime > 0) {
					Thread.sleep(sleepTime);
				}
			}
		} catch (Exception e) {
			log.log(Level.WARNING, "LoadTest generator stopped", e);
		}
		onTestFinish();
	}
}
