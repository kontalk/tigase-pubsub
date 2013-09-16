/*
 * ManageSubscriptionModule.java
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

import tigase.component2.PacketWriter;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.Affiliation;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.Subscription;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.RepositoryException;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.pubsub.repository.stateless.UsersSubscription;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;

/**
 * Class description
 * 
 * 
 * @version 5.0.0, 2010.03.27 at 05:25:49 GMT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
public class ManageSubscriptionModule extends AbstractPubSubModule {
	private static final Criteria CRIT = ElementCriteria.name("iq").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub#owner")).add(
			ElementCriteria.name("subscriptions"));

	private static Packet createSubscriptionNotification(JID fromJid, JID toJid, String nodeName, Subscription subscription) {
		Packet message = Message.getMessage(fromJid, toJid, null, null, null, null, null);
		Element pubsub = new Element("pubsub", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/pubsub" });

		message.getElement().addChild(pubsub);

		Element affilations = new Element("subscriptions", new String[] { "node" }, new String[] { nodeName });

		pubsub.addChild(affilations);
		affilations.addChild(new Element("subscription", new String[] { "jid", "subscription" }, new String[] {
				toJid.toString(), subscription.name() }));

		return message;
	}

	/**
	 * Constructs ...
	 * 
	 * 
	 * @param config
	 * @param pubsubRepository
	 */
	public ManageSubscriptionModule(PubSubConfig config, PacketWriter packetWriter) {
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
		return new String[] { "http://jabber.org/protocol/pubsub#manage-subscriptions" };
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
			BareJID toJid = packet.getStanzaTo().getBareJID();
			Element element = packet.getElement();
			Element pubsub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub#owner");
			Element subscriptions = pubsub.getChild("subscriptions");
			String nodeName = subscriptions.getAttributeStaticStr("node");
			StanzaType type = packet.getType();

			if ((type == null) || (type != StanzaType.get && type != StanzaType.set)) {
				throw new PubSubException(Authorization.BAD_REQUEST);
			}
			if (nodeName == null) {
				throw new PubSubException(Authorization.BAD_REQUEST, PubSubErrorCondition.NODE_REQUIRED);
			}

			AbstractNodeConfig nodeConfig = getRepository().getNodeConfig(toJid, nodeName);

			if (nodeConfig == null) {
				throw new PubSubException(Authorization.ITEM_NOT_FOUND);
			}

			ISubscriptions nodeSubscriptions = getRepository().getNodeSubscriptions(toJid, nodeName);
			IAffiliations nodeAffiliations = getRepository().getNodeAffiliations(toJid, nodeName);
			JID senderJid = packet.getStanzaFrom();

			if (!this.config.isAdmin(senderJid)) {
				UsersAffiliation senderAffiliation = nodeAffiliations.getSubscriberAffiliation(senderJid.getBareJID());

				if (senderAffiliation.getAffiliation() != Affiliation.owner) {
					throw new PubSubException(element, Authorization.FORBIDDEN);
				}
			}
			if (type == StanzaType.get) {
				processGet(packet, subscriptions, nodeName, nodeSubscriptions, packetWriter);
			} else {
				if (type == StanzaType.set) {
					processSet(packet, subscriptions, nodeName, nodeConfig, nodeSubscriptions, packetWriter);
				}
			}
			if (nodeSubscriptions.isChanged()) {
				getRepository().update(toJid, nodeName, nodeSubscriptions);
			}

		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}

	private void processGet(Packet packet, Element subscriptions, String nodeName, final ISubscriptions nodeSubscriptions,
			PacketWriter packetWriter) throws RepositoryException {
		Element ps = new Element("pubsub", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/pubsub#owner" });

		Packet iq = packet.okResult(ps, 0);

		Element afr = new Element("subscriptions", new String[] { "node" }, new String[] { nodeName });

		ps.addChild(afr);

		UsersSubscription[] subscribers = nodeSubscriptions.getSubscriptions();

		if (subscribers != null) {
			for (UsersSubscription usersSubscription : subscribers) {
				if (usersSubscription.getSubscription() == Subscription.none) {
					continue;
				}

				Element subscription = new Element("subscription", new String[] { "jid", "subscription" }, new String[] {
						usersSubscription.getJid().toString(), usersSubscription.getSubscription().name() });

				afr.addChild(subscription);
			}
		}
		packetWriter.write(iq);
	}

	private void processSet(Packet packet, Element subscriptions, String nodeName, final AbstractNodeConfig nodeConfig,
			final ISubscriptions nodeSubscriptions, PacketWriter packetWriter) throws PubSubException, RepositoryException {
		List<Element> subss = subscriptions.getChildren();

		for (Element a : subss) {
			if (!"subscription".equals(a.getName())) {
				throw new PubSubException(Authorization.BAD_REQUEST);
			}
		}
		for (Element af : subss) {
			String strSubscription = af.getAttributeStaticStr("subscription");
			String jidStr = af.getAttributeStaticStr("jid");
			JID jid = JID.jidInstanceNS(jidStr);

			if (strSubscription == null) {
				continue;
			}

			Subscription newSubscription = Subscription.valueOf(strSubscription);
			Subscription oldSubscription = nodeSubscriptions.getSubscription(jid.getBareJID());

			oldSubscription = (oldSubscription == null) ? Subscription.none : oldSubscription;
			if ((oldSubscription == Subscription.none) && (newSubscription != Subscription.none)) {
				nodeSubscriptions.addSubscriberJid(jid.getBareJID(), newSubscription);
				if (nodeConfig.isTigaseNotifyChangeSubscriptionAffiliationState()) {
					packetWriter.write(createSubscriptionNotification(packet.getStanzaTo(), jid, nodeName, newSubscription));
				}
			} else {
				nodeSubscriptions.changeSubscription(jid.getBareJID(), newSubscription);
				if (nodeConfig.isTigaseNotifyChangeSubscriptionAffiliationState()) {
					packetWriter.write(createSubscriptionNotification(packet.getStanzaTo(), jid, nodeName, newSubscription));
				}
			}
		}
		Packet iq = packet.okResult((Element) null, 0);
		packetWriter.write(iq);
	}
}
