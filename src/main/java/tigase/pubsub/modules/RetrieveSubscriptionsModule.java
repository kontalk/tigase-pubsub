/*
 * RetrieveSubscriptionsModule.java
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

import java.util.Map;
import tigase.component2.PacketWriter;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.Subscription;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IPubSubDAO;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.BareJID;

/**
 * Class description
 * 
 * 
 * @version 5.0.0, 2010.03.27 at 05:27:10 GMT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
public class RetrieveSubscriptionsModule extends AbstractPubSubModule {
	private static final Criteria CRIT = ElementCriteria.nameType("iq", "get").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub")).add(ElementCriteria.name("subscriptions"));

	/**
	 * Constructs ...
	 * 
	 * 
	 * @param config
	 * @param pubsubRepository
	 */
	public RetrieveSubscriptionsModule(PubSubConfig config, PacketWriter packetWriter) {
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
		return new String[] { "http://jabber.org/protocol/pubsub#retrieve-subscriptions" };
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
	 * @return
	 * 
	 * @throws PubSubException
	 */
	@Override
	public void process(Packet packet) throws PubSubException {
		try {
			final BareJID serviceJid = packet.getStanzaTo().getBareJID();
			final Element pubsub = packet.getElement().getChild("pubsub", "http://jabber.org/protocol/pubsub");
			final Element subscriptions = pubsub.getChild("subscriptions");
			final String nodeName = subscriptions.getAttributeStaticStr("node");
			final String senderJid = packet.getStanzaFrom().toString();
			final BareJID senderBareJid = packet.getStanzaFrom().getBareJID();
			final Element pubsubResult = new Element("pubsub", new String[] { "xmlns" },
					new String[] { "http://jabber.org/protocol/pubsub" });

			Packet result = packet.okResult(pubsubResult, 0);

			final Element subscriptionsResult = new Element("subscriptions");

			pubsubResult.addChild(subscriptionsResult);
			if (nodeName == null) {
				IPubSubDAO directRepo = this.getRepository().getPubSubDAO();
				Map<String, UsersSubscription> usersSubscriptions = directRepo.getUserSubscriptions(serviceJid, senderBareJid);
				for (Map.Entry<String, UsersSubscription> entry : usersSubscriptions.entrySet()) {
					UsersSubscription subscription = entry.getValue();
					Element a = new Element("subscription", new String[] { "node", "jid", "subscription" },
						new String[] { entry.getKey(), senderBareJid.toString(), subscription.getSubscription().name() });

					subscriptionsResult.addChild(a);					
				}
			} else {
				ISubscriptions nodeSubscriptions = getRepository().getNodeSubscriptions(serviceJid, nodeName);

				subscriptionsResult.addAttribute("node", nodeName);

				UsersSubscription[] subscribers = nodeSubscriptions.getSubscriptions();

				for (final UsersSubscription usersSubscription : subscribers) {
					Element s = new Element("subscription", new String[] { "jid", "subscription", "subid" }, new String[] {
							usersSubscription.getJid().toString(), usersSubscription.getSubscription().name(),
							usersSubscription.getSubid() });

					subscriptionsResult.addChild(s);
				}
			}

			packetWriter.write(result);
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}
}
