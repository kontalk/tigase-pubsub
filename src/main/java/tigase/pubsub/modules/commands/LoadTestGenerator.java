package tigase.pubsub.modules.commands;

import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.server.AbstractMessageReceiver;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

public class LoadTestGenerator implements Runnable {

	private final AbstractMessageReceiver component;

	private int counter = 0;

	private final long delay;

	protected final Logger log = Logger.getLogger(this.getClass().getName());

	private String nodeName;
	
	private JID packetFromJid;

	private Element payload;

	private BareJID publisher;

	private BareJID serviceJid;

	/**
	 * Test time in seconds.
	 */
	private final long testTime;

	private final boolean useBlockingMethod;

	public LoadTestGenerator(AbstractMessageReceiver component, BareJID serviceJid, String node, BareJID publisher, long time,
			long frequency, int messageLength, boolean useBlockingMethod) {
		this.component = component;
		this.serviceJid = serviceJid;
		this.nodeName = node;
		this.publisher = publisher;
		this.testTime = time;
		this.useBlockingMethod = useBlockingMethod;
		this.delay = (long) ((1.0 / frequency) * 1000.0);
		this.packetFromJid = JID.jidInstanceNS("sess-man", serviceJid.getDomain(), null);

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
			final long testEndTime = testStartTime + testTime * 1000;
			long cst;
			while (testEndTime >= (cst = System.currentTimeMillis())) {
				++counter;
				Element item = new Element("item", new String[] { "id" }, new String[] { counter + "-" + testEndTime });
				item.addChild(payload);

				Element iq = new Element("iq", new String[] { "type", "from", "to", "id" }, new String[] { "set",
						publisher.toString(), serviceJid.toString(), "pub-" + counter + "-" + testEndTime });

				Element pubsub = new Element("pubsub", new String[] { "xmlns" },
						new String[] { "http://jabber.org/protocol/pubsub" });
				iq.addChild(pubsub);

				Element publish = new Element("publish", new String[] { "node" }, new String[] { nodeName });
				pubsub.addChild(publish);

				publish.addChild(item);

				Packet p = Packet.packetInstance(iq);
				p.setXMLNS(Packet.CLIENT_XMLNS);
				p.setPacketFrom(packetFromJid);

				if (component != null) {
					if (useBlockingMethod)
						component.addPacket(p);
					else
						component.addPacketNB(p);
				}
				// publishItemModule.publish(serviceJid, publisher, nodeName,
				// item);

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
