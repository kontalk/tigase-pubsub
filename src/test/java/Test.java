import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;

import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.pubsub.LeafNodeConfig;
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
		MockRepository repo = new MockRepository();

		System.out.println("Init test enviroment");
		pubsub = new PubSubComponent();

		try {
			pubsub.initialize(new String[] { "alice@localhost" }, null, repo, new LeafNodeConfig("default"));
		} catch (Exception e) {
			Assert.fail(e.getMessage());
		}
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


	@org.junit.Test
	public void test_createNode() {
		test("src/test/scripts/createNode.cor", xmlio);
	}
}
