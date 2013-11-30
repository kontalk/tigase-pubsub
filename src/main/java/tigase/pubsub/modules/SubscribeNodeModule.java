/*
 * SubscribeNodeModule.java
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

import java.util.ArrayList;
import java.util.List;

import tigase.component2.PacketWriter;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.AbstractPubSubModule;
import tigase.pubsub.AccessModel;
import tigase.pubsub.Affiliation;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.SendLastPublishedItem;
import tigase.pubsub.Subscription;
import tigase.pubsub.Utils;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

/**
 * Class description
 * 
 * 
 */
public class SubscribeNodeModule extends AbstractPubSubModule {
	private static final Criteria CRIT_SUBSCRIBE = ElementCriteria.nameType("iq", "set").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub")).add(ElementCriteria.name("subscribe"));

	private static Affiliation calculateNewOwnerAffiliation(final Affiliation ownerAffiliation, final Affiliation newAffiliation) {
		if (ownerAffiliation.getWeight() > newAffiliation.getWeight()) {
			return ownerAffiliation;
		} else {
			return newAffiliation;
		}
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param nodeName
	 * @param subscriberJid
	 * @param newSubscription
	 * @param subid
	 * 
	 * @return
	 */
	public static Element makeSubscription(String nodeName, BareJID subscriberJid, Subscription newSubscription, String subid) {
		Element resPubSub = new Element("pubsub", new String[] { "xmlns" },
				new String[] { "http://jabber.org/protocol/pubsub" });
		Element resSubscription = new Element("subscription");

		resPubSub.addChild(resSubscription);
		resSubscription.setAttribute("node", nodeName);
		resSubscription.setAttribute("jid", subscriberJid.toString());
		resSubscription.setAttribute("subscription", newSubscription.name());
		if (subid != null) {
			resSubscription.setAttribute("subid", subid);
		}

		return resPubSub;
	}

	private final PendingSubscriptionModule pendingSubscriptionModule;

	private PublishItemModule publishItemModule;

	/**
	 * Constructs ...
	 * 
	 * 
	 * @param config
	 * @param pubsubRepository
	 * @param manageSubscriptionModule
	 */
	public SubscribeNodeModule(PubSubConfig config, PacketWriter packetWriter,
			PendingSubscriptionModule manageSubscriptionModule, PublishItemModule publishItemModule) {
		super(config, packetWriter);
		this.pendingSubscriptionModule = manageSubscriptionModule;
		this.publishItemModule = publishItemModule;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/pubsub#manage-subscriptions",
				"http://jabber.org/protocol/pubsub#auto-subscribe", "http://jabber.org/protocol/pubsub#subscribe",
				"http://jabber.org/protocol/pubsub#subscription-notifications" };
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public Criteria getModuleCriteria() {
		return CRIT_SUBSCRIBE;
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
		final BareJID serviceJid = packet.getStanzaTo().getBareJID();
		final Element pubSub = packet.getElement().getChild("pubsub", "http://jabber.org/protocol/pubsub");
		final Element subscribe = pubSub.getChild("subscribe");
		final JID senderJid = packet.getStanzaFrom();
		final String nodeName = subscribe.getAttributeStaticStr("node");
		final BareJID jid = BareJID.bareJIDInstanceNS(subscribe.getAttributeStaticStr("jid"));

		try {
			AbstractNodeConfig nodeConfig = getRepository().getNodeConfig(serviceJid, nodeName);

			if (nodeConfig == null) {
				throw new PubSubException(packet.getElement(), Authorization.ITEM_NOT_FOUND);
			}
			if ((nodeConfig.getNodeAccessModel() == AccessModel.open)
					&& !Utils.isAllowedDomain(senderJid.getBareJID(), nodeConfig.getDomains())) {
				throw new PubSubException(Authorization.FORBIDDEN, "User blocked by domain");
			}

			IAffiliations nodeAffiliations = getRepository().getNodeAffiliations(serviceJid, nodeName);
			UsersAffiliation senderAffiliation = nodeAffiliations.getSubscriberAffiliation(senderJid.getBareJID());

			if (!this.config.isAdmin(senderJid) && (senderAffiliation.getAffiliation() != Affiliation.owner)
					&& !jid.equals((senderJid.getBareJID()))) {
				throw new PubSubException(packet.getElement(), Authorization.BAD_REQUEST, PubSubErrorCondition.INVALID_JID);
			}

			ISubscriptions nodeSubscriptions = getRepository().getNodeSubscriptions(serviceJid, nodeName);

			// TODO 6.1.3.2 Presence Subscription Required
			// TODO 6.1.3.3 Not in Roster Group
			// TODO 6.1.3.4 Not on Whitelist
			// TODO 6.1.3.5 Payment Required
			// TODO 6.1.3.6 Anonymous NodeSubscriptions Not Allowed
			// TODO 6.1.3.9 NodeSubscriptions Not Supported
			// TODO 6.1.3.10 Node Has Moved
			Subscription subscription = nodeSubscriptions.getSubscription(jid);

			if (senderAffiliation != null) {
				if (!senderAffiliation.getAffiliation().isSubscribe()) {
					throw new PubSubException(Authorization.FORBIDDEN, "Not enough privileges to subscribe");
				}
			}

			AccessModel accessModel = nodeConfig.getNodeAccessModel();

			if (subscription != null) {
				if ((subscription == Subscription.pending)
						&& !(this.config.isAdmin(senderJid) || (senderAffiliation.getAffiliation() == Affiliation.owner))) {
					throw new PubSubException(Authorization.FORBIDDEN, PubSubErrorCondition.PENDING_SUBSCRIPTION,
							"Subscription is pending");
				}
			}
			if ((accessModel == AccessModel.whitelist)
					&& ((senderAffiliation == null) || (senderAffiliation.getAffiliation() == Affiliation.none) || (senderAffiliation.getAffiliation() == Affiliation.outcast))) {
				throw new PubSubException(Authorization.NOT_ALLOWED, PubSubErrorCondition.CLOSED_NODE);
			}

			List<Packet> results = new ArrayList<Packet>();
			Subscription newSubscription;
			Affiliation affiliation = nodeAffiliations.getSubscriberAffiliation(jid).getAffiliation();

			if (this.config.isAdmin(senderJid) || (senderAffiliation.getAffiliation() == Affiliation.owner)) {
				newSubscription = Subscription.subscribed;
				affiliation = calculateNewOwnerAffiliation(affiliation, Affiliation.member);
			} else if (accessModel == AccessModel.open) {
				newSubscription = Subscription.subscribed;
				affiliation = calculateNewOwnerAffiliation(affiliation, Affiliation.member);
			} else if (accessModel == AccessModel.authorize) {
				newSubscription = Subscription.pending;
				affiliation = calculateNewOwnerAffiliation(affiliation, Affiliation.none);
			} else if (accessModel == AccessModel.presence) {
				boolean allowed = hasSenderSubscription(jid, nodeAffiliations, nodeSubscriptions);

				if (!allowed) {
					throw new PubSubException(Authorization.NOT_AUTHORIZED, PubSubErrorCondition.PRESENCE_SUBSCRIPTION_REQUIRED);
				}
				newSubscription = Subscription.subscribed;
				affiliation = calculateNewOwnerAffiliation(affiliation, Affiliation.member);
			} else if (accessModel == AccessModel.roster) {
				boolean allowed = isSenderInRosterGroup(jid, nodeConfig, nodeAffiliations, nodeSubscriptions);

				if (!allowed) {
					throw new PubSubException(Authorization.NOT_AUTHORIZED, PubSubErrorCondition.NOT_IN_ROSTER_GROUP);
				}
				newSubscription = Subscription.subscribed;
				affiliation = calculateNewOwnerAffiliation(affiliation, Affiliation.member);
			} else if (accessModel == AccessModel.whitelist) {
				newSubscription = Subscription.subscribed;
				affiliation = calculateNewOwnerAffiliation(affiliation, Affiliation.member);
			} else {
				throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED, "AccessModel '" + accessModel.name()
						+ "' is not implemented yet");
			}

			String subid = nodeSubscriptions.getSubscriptionId(jid);

			boolean sendLastPublishedItem = false;

			if (subid == null) {
				subid = nodeSubscriptions.addSubscriberJid(jid, newSubscription);
				nodeAffiliations.addAffiliation(jid, affiliation);
				if ((accessModel == AccessModel.authorize)
						&& !(this.config.isAdmin(senderJid) || (senderAffiliation.getAffiliation() == Affiliation.owner))) {
					results.addAll(this.pendingSubscriptionModule.sendAuthorizationRequest(nodeName, packet.getStanzaTo(),
							subid, jid, nodeAffiliations));
				}
				sendLastPublishedItem = nodeConfig.getSendLastPublishedItem() == SendLastPublishedItem.on_sub;
			} else {
				nodeSubscriptions.changeSubscription(jid, newSubscription);
				nodeAffiliations.changeAffiliation(jid, affiliation);
			}

			// getRepository().setData(config.getServiceName(), nodeName,
			// "owner",
			// JIDUtils.getNodeID(element.getAttribute("from")));
			if (nodeSubscriptions.isChanged()) {
				this.getRepository().update(serviceJid, nodeName, nodeSubscriptions);
			}
			if (nodeAffiliations.isChanged()) {
				this.getRepository().update(serviceJid, nodeName, nodeAffiliations);
			}
			Packet result = packet.okResult(makeSubscription(nodeName, jid, newSubscription, subid), 0);

			results.add(result);

			packetWriter.write(results);

			if (sendLastPublishedItem) {
				publishItemModule.publishLastItem(serviceJid, nodeConfig, JID.jidInstance(jid));
			}
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}
}
