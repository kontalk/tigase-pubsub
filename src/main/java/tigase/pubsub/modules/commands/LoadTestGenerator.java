package tigase.pubsub.modules.commands;

import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.pubsub.modules.PublishItemModule;
import tigase.xml.Element;
import tigase.xmpp.BareJID;

public class LoadTestGenerator implements Runnable {

	private int counter = 0;

	private final long delay;

	protected final Logger log = Logger.getLogger(this.getClass().getName());

	private String nodeName;

	private Element payload;

	private String publisher;

	private final PublishItemModule publishItemModule;

	private BareJID serviceJid;

	/**
	 * Test time in seconds.
	 */
	private final long testTime;

	public LoadTestGenerator(PublishItemModule publishItemModule, BareJID serviceJid, String node, long time, long frequency,
			int messageLength) {
		this.publishItemModule = publishItemModule;
		this.serviceJid = serviceJid;
		this.nodeName = node;
		this.publisher = serviceJid.toString();
		this.testTime = time;
		this.delay = (long) (1.0 / (frequency / 60.0) * 1000.0);

		String x = "";
		for (int i = 0; i < messageLength; i++) {
			x += "a";
		}

		this.payload = new Element("payload", x);

	}

	@Override
	public void run() {
		try {
			final long testStartTime = System.currentTimeMillis();
			final long testEndTime = testStartTime + testTime * 1000 + delay;
			long cst;
			while (testEndTime > (cst = System.currentTimeMillis())) {
				++counter;
				Element item = new Element("item", new String[] { "id" }, new String[] { counter + "-" + testEndTime });
				item.addChild(payload);

				if (publishItemModule != null)
					publishItemModule.publish(serviceJid, publisher, nodeName, item);

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
	}
}
