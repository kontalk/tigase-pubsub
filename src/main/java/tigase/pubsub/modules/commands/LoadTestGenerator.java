package tigase.pubsub.modules.commands;

import java.util.logging.Logger;

import tigase.server.AbstractMessageReceiver;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

public class LoadTestGenerator extends AbstractLoadRunner {

	private final AbstractMessageReceiver component;


	protected final Logger log = Logger.getLogger(this.getClass().getName());

	private String nodeName;
	
	private JID packetFromJid;

	private Element payload;

	private BareJID publisher;

	private BareJID serviceJid;


	private final boolean useBlockingMethod;

	public LoadTestGenerator(AbstractMessageReceiver component, BareJID serviceJid, String node, BareJID publisher, long time,
			long frequency, int messageLength, boolean useBlockingMethod) {
		super(time, frequency);
		this.component = component;
		this.serviceJid = serviceJid;
		this.nodeName = node;
		this.publisher = publisher;
		this.useBlockingMethod = useBlockingMethod;
		this.packetFromJid = JID.jidInstanceNS("sess-man", serviceJid.getDomain(), null);

		String x = "";
		for (int i = 0; i < messageLength; i++) {
			x += "a";
		}

		this.payload = new Element("payload", x);

	}


	protected void doWork() throws Exception {
		Element item = new Element("item", new String[] { "id" }, new String[] { getCounter() + "-" + getTestEndTime() });
		item.addChild(payload);

		Element iq = new Element("iq", new String[] { "type", "from", "to", "id" }, new String[] { "set", publisher.toString(),
				serviceJid.toString(), "pub-" + getCounter() + "-" + getTestEndTime() });

		Element pubsub = new Element("pubsub", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/pubsub" });
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
	}


}
