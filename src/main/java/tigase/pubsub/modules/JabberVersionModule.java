/*
 * JabberVersionModule.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2012 "Artur Hefczyc" <artur.hefczyc@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package tigase.pubsub.modules;

import tigase.component.PacketWriter;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.PubSubVersion;
import tigase.pubsub.exceptions.PubSubException;
import tigase.server.Packet;
import tigase.xml.Element;

/**
 * Class description
 * 
 * 
 */
public class JabberVersionModule extends AbstractPubSubModule {

	private static final Criteria CRIT = ElementCriteria.nameType("iq", "get").add(
			ElementCriteria.name("query", "jabber:iq:version"));

	public JabberVersionModule(PubSubConfig config, PacketWriter packetWriter) {
		super(config, packetWriter);
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public String[] getFeatures() {
		return null;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param packet
	 * @param packetWriter
	 * 
	 * @return
	 * 
	 * @throws PubSubException
	 */
	@Override
	public void process(Packet packet) throws PubSubException {
		Element query = new Element("query", new String[] { "xmlns" }, new String[] { "jabber:iq:version" });

		query.addChild(new Element("name", "Tigase PubSub"));
		query.addChild(new Element("version", PubSubVersion.getVersion()));
		query.addChild(new Element("os", System.getProperty("os.name") + "-" + System.getProperty("os.arch") + "-"
				+ System.getProperty("os.version") + ", " + System.getProperty("java.vm.name") + "-"
				+ System.getProperty("java.version") + " " + System.getProperty("java.vm.vendor")));

		packetWriter.write(packet.okResult(query, 0));
	}

}
