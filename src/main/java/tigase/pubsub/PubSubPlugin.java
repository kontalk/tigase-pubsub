/*
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2007 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.pubsub;

import java.util.Map;
import java.util.Queue;
import java.util.logging.Logger;

import tigase.db.NonAuthUserRepository;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.XMPPException;
import tigase.xmpp.XMPPResourceConnection;
import tigase.xmpp.impl.SimpleForwarder;

public class PubSubPlugin extends SimpleForwarder {

	private static final Element[] DISCO_FEATURES = { new Element("identity", new String[] { "category", "type" }, new String[] {
			"pubsub", "pep" }), };

	private static final String[] ELEMENTS = { "pubsub" };

	private static final Object PUBSUB_COMPONENT_URL = "pubsub-component";

	private static final String XMLNS = "http://jabber.org/protocol/pubsub";

	private static final String[] XMLNSS = { XMLNS };

	private final Logger log = Logger.getLogger(this.getClass().getName());

	@Override
	public String id() {
		return "pubsub";
	}

	@Override
	public void process(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo, Queue<Packet> results,
			Map<String, Object> settings) throws XMPPException {
		final String pubSubComponentUrl = (String) settings.get(PUBSUB_COMPONENT_URL);

		if (packet.getElemTo() == null || packet.getElemTo().equals("sphere"))
			packet.getElement().setAttribute("to", pubSubComponentUrl);
		super.process(packet, session, repo, results, settings);
	}

	@Override
	public Element[] supDiscoFeatures(final XMPPResourceConnection session) {
		return DISCO_FEATURES;
	}

	@Override
	public String[] supElements() {
		return ELEMENTS;
	}

	@Override
	public String[] supNamespaces() {
		return XMLNSS;
	}
}
