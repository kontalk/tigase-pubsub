import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;

import tigase.pubsub.PubSubComponent;
import tigase.test.junit.JUnitXMLIO;
import tigase.test.junit.XMPPTestCase;
import tigase.xml.Element;
import tigase.xmpp.PacketErrorTypeException;

public class Test extends XMPPTestCase {

	private JUnitXMLIO xmlio;

	private PubSubComponent pubsub;

	@Before
	public void init() {
		System.out.println("Init test enviroment");
		pubsub = new PubSubComponent();
		Map<String, Object> props = new HashMap<String, Object>();
		props.put("admin", new String[] { "alice@localhost" });
		props.put("pubsub-repo-class", "tigase.db.xml.XMLRepository");
		props.put("pubsub-repo-url", "user-repository.xml");
		props.put("max-queue-size", new Integer(1000));

		pubsub.setProperties(props);

		xmlio = new JUnitXMLIO() {

			@Override
			public void write(Element data) throws IOException {
				try {
					send(pubsub.process(data));
				} catch (PacketErrorTypeException e) {
					throw new RuntimeException("", e);
				}
			}

			@Override
			public void close() {
				// TODO Auto-generated method stub

			}
		};

	}

	@org.junit.Test
	public void test_pings() {
		test("src/test/scripts/ping.cor", xmlio);
	}

}
