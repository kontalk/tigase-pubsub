/*
 * Tigase Jabber/XMPP Publish Subscribe Component
 * Copyright (C) 2007 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
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
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.pubsub.modules;

import java.util.ArrayList;
import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractModule;
import tigase.pubsub.AbstractNodeConfig;
import tigase.pubsub.AccessModel;
import tigase.pubsub.Affiliation;
import tigase.pubsub.ElementWriter;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.Subscription;
import tigase.pubsub.Utils;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IAffiliations;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.pubsub.repository.ISubscriptions;
import tigase.pubsub.repository.stateless.UsersAffiliation;
import tigase.util.JIDUtils;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

public class SubscribeNodeModule extends AbstractModule {

	private static final Criteria CRIT_SUBSCRIBE = ElementCriteria.nameType("iq", "set").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub")).add(ElementCriteria.name("subscribe"));

	private static Affiliation calculateNewOwnerAffiliation(final Affiliation ownerAffiliation, final Affiliation newAffiliation) {
		if (ownerAffiliation.getWeight() > newAffiliation.getWeight()) {
			return ownerAffiliation;
		} else {
			return newAffiliation;
		}
	}

	public static Element makeSubscription(String nodeName, String subscriberJid, Subscription newSubscription, String subid) {
		Element resPubSub = new Element("pubsub", new String[] { "xmlns" },
				new String[] { "http://jabber.org/protocol/pubsub" });
		Element resSubscription = new Element("subscription");
		resPubSub.addChild(resSubscription);
		resSubscription.setAttribute("node", nodeName);
		resSubscription.setAttribute("jid", subscriberJid);
		resSubscription.setAttribute("subscription", newSubscription.name());
		if (subid != null)
			resSubscription.setAttribute("subid", subid);
		return resPubSub;
	}

	private final PendingSubscriptionModule pendingSubscriptionModule;

	public SubscribeNodeModule(PubSubConfig config, IPubSubRepository pubsubRepository,
			PendingSubscriptionModule manageSubscriptionModule) {
		super(config, pubsubRepository);
		this.pendingSubscriptionModule = manageSubscriptionModule;
	}

	@Override
	public String[] getFeatures() {
		return new String[] { "http://jabber.org/protocol/pubsub#manage-subscriptions",
				"http://jabber.org/protocol/pubsub#auto-subscribe", "http://jabber.org/protocol/pubsub#subscribe",
				"http://jabber.org/protocol/pubsub#subscription-notifications" };
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT_SUBSCRIBE;
	}

	@Override
	public List<Element> process(Element element, ElementWriter elementWriter) throws PubSubException {
		final Element pubSub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub");
		final Element subscribe = pubSub.getChild("subscribe");
		final String senderJid = element.getAttribute("from");
		final String nodeName = subscribe.getAttribute("node");
		final String jid = subscribe.getAttribute("jid");

		try {
			AbstractNodeConfig nodeConfig = repository.getNodeConfig(nodeName);
			if (nodeConfig == null) {
				throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
			}
			if (nodeConfig.getNodeAccessModel() == AccessModel.open
					&& !Utils.isAllowedDomain(senderJid, nodeConfig.getDomains()))
				throw new PubSubException(Authorization.FORBIDDEN, "User blocked by domain");

			IAffiliations nodeAffiliations = repository.getNodeAffiliations(nodeName);
			UsersAffiliation senderAffiliation = nodeAffiliations.getSubscriberAffiliation(senderJid);

			if (!this.config.isAdmin(JIDUtils.getNodeID(senderJid)) && senderAffiliation.getAffiliation() != Affiliation.owner
					&& !JIDUtils.getNodeID(jid).equals(JIDUtils.getNodeID(senderJid))) {
				throw new PubSubException(element, Authorization.BAD_REQUEST, PubSubErrorCondition.INVALID_JID);
			}

			ISubscriptions nodeSubscriptions = repository.getNodeSubscriptions(nodeName);

			// TODO 6.1.3.2 Presence Subscription Required
			// TODO 6.1.3.3 Not in Roster Group
			// TODO 6.1.3.4 Not on Whitelist
			// TODO 6.1.3.5 Payment Required
			// TODO 6.1.3.6 Anonymous NodeSubscriptions Not Allowed
			// TODO 6.1.3.9 NodeSubscriptions Not Supported
			// TODO 6.1.3.10 Node Has Moved

			Subscription subscription = nodeSubscriptions.getSubscription(jid);

			if (senderAffiliation != null) {
				if (!senderAffiliation.getAffiliation().isSubscribe())
					throw new PubSubException(Authorization.FORBIDDEN, "Not enough privileges to subscribe");
			}

			AccessModel accessModel = nodeConfig.getNodeAccessModel();

			if (subscription != null) {
				if (subscription == Subscription.pending
						&& !(this.config.isAdmin(JIDUtils.getNodeID(senderJid)) || senderAffiliation.getAffiliation() == Affiliation.owner)) {
					throw new PubSubException(Authorization.FORBIDDEN, PubSubErrorCondition.PENDING_SUBSCRIPTION,
							"Subscription is pending");
				}
			}
			if (accessModel == AccessModel.whitelist
					&& (senderAffiliation == null || senderAffiliation.getAffiliation() == Affiliation.none || senderAffiliation.getAffiliation() == Affiliation.outcast)) {
				throw new PubSubException(Authorization.NOT_ALLOWED, PubSubErrorCondition.CLOSED_NODE);
			}

			List<Element> results = new ArrayList<Element>();
			Subscription newSubscription;
			Affiliation affiliation = nodeAffiliations.getSubscriberAffiliation(jid).getAffiliation();

			if (this.config.isAdmin(JIDUtils.getNodeID(senderJid)) || senderAffiliation.getAffiliation() == Affiliation.owner) {
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
				if (!allowed)
					throw new PubSubException(Authorization.NOT_AUTHORIZED, PubSubErrorCondition.PRESENCE_SUBSCRIPTION_REQUIRED);
				newSubscription = Subscription.subscribed;
				affiliation = calculateNewOwnerAffiliation(affiliation, Affiliation.member);
			} else if (accessModel == AccessModel.roster) {
				boolean allowed = isSenderInRosterGroup(jid, nodeConfig, nodeAffiliations, nodeSubscriptions);
				if (!allowed)
					throw new PubSubException(Authorization.NOT_AUTHORIZED, PubSubErrorCondition.NOT_IN_ROSTER_GROUP);
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
			if (subid == null) {
				subid = nodeSubscriptions.addSubscriberJid(jid, newSubscription);
				nodeAffiliations.addAffiliation(jid, affiliation);
				if (accessModel == AccessModel.authorize
						&& !(this.config.isAdmin(JIDUtils.getNodeID(senderJid)) || senderAffiliation.getAffiliation() == Affiliation.owner)) {
					results.addAll(this.pendingSubscriptionModule.sendAuthorizationRequest(nodeName,
							element.getAttribute("to"), subid, jid, nodeAffiliations));
				}

			} else {
				nodeSubscriptions.changeSubscription(jid, newSubscription);
				nodeAffiliations.changeAffiliation(jid, affiliation);
			}

			// repository.setData(config.getServiceName(), nodeName, "owner",
			// JIDUtils.getNodeID(element.getAttribute("from")));

			Element result = createResultIQ(element);

			if (nodeSubscriptions.isChanged()) {
				this.repository.update(nodeName, nodeSubscriptions);
			}
			if (nodeAffiliations.isChanged()) {
				this.repository.update(nodeName, nodeAffiliations);
			}
			result.addChild(makeSubscription(nodeName, jid, newSubscription, subid));

			results.add(result);

			return results;
		} catch (PubSubException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

	}

}
