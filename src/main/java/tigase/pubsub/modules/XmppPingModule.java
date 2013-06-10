/*
 * XmppPingModule.java
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

//~--- non-JDK imports --------------------------------------------------------

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.criteria.Or;

import tigase.pubsub.ElementWriter;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.Module;

import tigase.xml.Element;

//~--- JDK imports ------------------------------------------------------------

import java.util.ArrayList;
import java.util.List;
import tigase.pubsub.PacketWriter;
import tigase.server.Packet;

/**
 * Class description
 *
 *
 * @version        Enter version here..., 13/02/20
 * @author         Enter your name here...
 */
public class XmppPingModule
				implements Module {
	private static final Criteria CRIT =
		ElementCriteria.nameType("iq", "get").add(
				new Or(ElementCriteria.name(
					"ping",
					"http://www.xmpp.org/extensions/xep-0199.html#ns"), ElementCriteria.name(
						"ping", "urn:xmpp:ping")));

	//~--- get methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public String[] getFeatures() {
		return new String[] { "urn:xmpp:ping" };
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

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param iq
	 * @param packetWriter
	 *
	 * @return
	 *
	 * @throws PubSubException
	 */
	@Override
	public List<Packet> process(Packet iq, PacketWriter elementWriter)
					throws PubSubException {
		Packet reposnse = iq.okResult((Element) null, 0);
		List<Packet> x = new ArrayList<Packet>();

		x.add(reposnse);

		return x;
	}
}


//~ Formatted in Tigase Code Convention on 13/02/20
