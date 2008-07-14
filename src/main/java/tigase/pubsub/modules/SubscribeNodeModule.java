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
package tigase.pubsub.modules;

import java.util.ArrayList;
import java.util.List;

import com.sun.imageio.plugins.common.SubImageInputStream;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.pubsub.AbstractModule;
import tigase.pubsub.AccessModel;
import tigase.pubsub.Affiliation;
import tigase.pubsub.NodeType;
import tigase.pubsub.PubSubConfig;
import tigase.pubsub.Subscription;
import tigase.pubsub.exceptions.PubSubErrorCondition;
import tigase.pubsub.exceptions.PubSubException;
import tigase.pubsub.repository.IPubSubRepository;
import tigase.util.JIDUtils;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

public class SubscribeNodeModule extends AbstractModule {

	private static final Criteria CRIT_SUBSCRIBE = ElementCriteria.nameType("iq", "set").add(
			ElementCriteria.name("pubsub", "http://jabber.org/protocol/pubsub")).add(ElementCriteria.name("subscribe"));

	public static Element makeSubscription(String nodeName, String subscriberJid, Subscription newSubscription, String subid) {
		Element resPubSub = new Element("pubsub", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/pubsub" });
		Element resSubscription = new Element("subscription");
		resPubSub.addChild(resSubscription);
		resSubscription.setAttribute("node", nodeName);
		resSubscription.setAttribute("jid", subscriberJid);
		resSubscription.setAttribute("subscription", newSubscription.name());
		if (subid != null)
			resSubscription.setAttribute("subid", subid);
		return resPubSub;
	}

	private final ManageSubscriptionModule manageSubscriptionModule;

	public SubscribeNodeModule(PubSubConfig config, IPubSubRepository pubsubRepository,
			ManageSubscriptionModule manageSubscriptionModule) {
		super(config, pubsubRepository);
		this.manageSubscriptionModule = manageSubscriptionModule;
	}

	@Override
	public String[] getFeatures() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT_SUBSCRIBE;
	}

	@Override
	public List<Element> process(Element element) throws PubSubException {
		final Element pubSub = element.getChild("pubsub", "http://jabber.org/protocol/pubsub");
		final Element subscribe = pubSub.getChild("subscribe");

		final String nodeName = subscribe.getAttribute("node");
		final String jid = subscribe.getAttribute("jid");

		try {
			NodeType nodeType = repository.getNodeType(nodeName);
			if (nodeType == null) {
				throw new PubSubException(element, Authorization.ITEM_NOT_FOUND);
			}

			Affiliation senderAffiliation = repository.getSubscriberAffiliation(nodeName, jid);

			if (senderAffiliation != Affiliation.owner
					&& !JIDUtils.getNodeID(jid).equals(JIDUtils.getNodeID(element.getAttribute("from")))) {
				throw new PubSubException(element, Authorization.BAD_REQUEST, PubSubErrorCondition.INVALID_JID);
			}

			// TODO 6.1.3.2 Presence Subscription Required
			// TODO 6.1.3.3 Not in Roster Group
			// TODO 6.1.3.4 Not on Whitelist
			// TODO 6.1.3.5 Payment Required
			// TODO 6.1.3.6 Anonymous Subscriptions Not Allowed
			// TODO 6.1.3.9 Subscriptions Not Supported
			// TODO 6.1.3.10 Node Has Moved

			Subscription subscription = repository.getSubscription(nodeName, jid);

			if (senderAffiliation != null) {
				if (senderAffiliation == Affiliation.outcast)
					throw new PubSubException(Authorization.FORBIDDEN);
			}

			AccessModel accessModel = repository.getNodeAccessModel(nodeName);

			if (subscription != null) {
				if (subscription == Subscription.pending) {
					throw new PubSubException(Authorization.FORBIDDEN, PubSubErrorCondition.PENDING_SUBSCRIPTION);
				}
			}
			if (accessModel == AccessModel.whitelist && (senderAffiliation == null || senderAffiliation == Affiliation.none)) {
				throw new PubSubException(Authorization.NOT_ALLOWED, PubSubErrorCondition.CLOSED_NODE);
			}

			List<Element> results = new ArrayList<Element>();
			Subscription newSubscription;
			Affiliation affiliation;

			if (accessModel == AccessModel.open) {
				newSubscription = Subscription.subscribed;
				affiliation = Affiliation.member;
			} else if (accessModel == AccessModel.authorize) {
				newSubscription = Subscription.pending;
				affiliation = Affiliation.none;
			} else {
				throw new PubSubException(Authorization.FEATURE_NOT_IMPLEMENTED, "AccessModel '" + accessModel.name()
						+ "' is not implemented yet");
			}

			String subid = repository.getSubscriptionId(nodeName, jid);
			if (senderAffiliation == null) {
				subid = repository.addSubscriberJid(nodeName, jid, affiliation, newSubscription);
				if (accessModel == AccessModel.authorize) {
					results.addAll(this.manageSubscriptionModule.sendAuthorizationRequest(nodeName, element.getAttribute("to"),
							subid, jid));
				}

			} else {
				repository.changeSubscription(nodeName, jid, newSubscription);
			}

			// repository.setData(config.getServiceName(), nodeName, "owner",
			// JIDUtils.getNodeID(element.getAttribute("from")));

			Element result = createResultIQ(element);

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
