/*
 * RetrieveAffiliationsModule.java
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

import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractModule;
import tigase.pubsub.Affiliation;
import tigase.pubsub.PacketWriter;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IPubSubDAO;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.server.Packet;
import tigase.xml.Element;

/**
 * Class description
 * 
 * 
 * @version Enter version here..., 13/02/20
 * @author Enter your name here...
 */
public class RetrieveAffiliationsModule extends AbstractModule {
	private static final Criteria CRIT = ElementCriteria.nameType("iq", "get").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub")).add(ElementCriteria.name("affiliations"));

	/**
	 * Constructs ...
	 * 
	 * 
	 * @param config
	 * @param pubsubRepository
	 */
	public RetrieveAffiliationsModule(PubSubConfig config, IPubSubRepository pubsubRepository) {
		super(config, pubsubRepository);
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/pubsub#retrieve-affiliations",
				"http://jabber.org/protocol/pubsub#publisher-affiliation",
				"http://jabber.org/protocol/pubsub#outcast-affiliation", "http://jabber.org/protocol/pubsub#member-affiliation" };
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
	public List<Packet> process(Packet packet, PacketWriter packetWriter) throws PubSubException {
		try {
			final Element pubsub = packet.getElement().getChild("pubsub", "http://jabber.org/protocol/pubsub");
			final Element affiliations = pubsub.getChild("affiliations");
			final String senderJid = packet.getStanzaFrom().toString();
			final String senderBareJid = packet.getStanzaFrom().getBareJID().toString();
			final Element pubsubResult = new Element("pubsub", new String[] { "xmlns" },
					new String[] { "http://jabber.org/protocol/pubsub" });

			final Packet result = packet.okResult(pubsubResult, 0);

			final Element affiliationsResult = new Element("affiliations");

			pubsubResult.addChild(affiliationsResult);

			IPubSubDAO directRepo = this.repository.getPubSubDAO();
			String[] nodes = directRepo.getNodesList();

			if (nodes != null) {
				for (String node : nodes) {
					UsersAffiliation[] affilitaions = directRepo.getNodeAffiliations(node).getAffiliations();

					if (affiliations != null) {
						for (UsersAffiliation usersAffiliation : affilitaions) {
							if (senderBareJid.equals(usersAffiliation.getJid())) {
								Affiliation affiliation = usersAffiliation.getAffiliation();
								Element a = new Element("affiliation", new String[] { "node", "affiliation" }, new String[] {
										node, affiliation.name() });

								affiliationsResult.addChild(a);
							}
						}
					}
				}
			}

			return makeArray(result);
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}
}
